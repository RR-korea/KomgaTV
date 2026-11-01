package com.komgatv.feature.reader.epub

import com.komgatv.core.data.epub.EpubResourceLoader
import com.komgatv.core.data.repository.BookPageResult
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.datastore.AuthConfig
import com.komgatv.core.datastore.ReaderPreferences
import com.komgatv.core.datastore.SavedServer
import com.komgatv.core.datastore.ServerConfig
import com.komgatv.core.datastore.ServerPreferences
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookMetadata
import com.komgatv.core.model.BookPage
import com.komgatv.core.model.EpubChapter
import com.komgatv.core.model.EpubManifest
import com.komgatv.core.model.EpubTocEntry
import com.komgatv.core.model.EpubPosition
import com.komgatv.core.model.MediaProfile
import com.komgatv.core.model.ReadProgress
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.model.displayName
import com.komgatv.core.network.ServerSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpubViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val books = FakeBookRepository()
    private val prefs = FakeReaderPreferences()
    private lateinit var viewModel: EpubViewModel

    private val base = "https://komga.example/api/v1/books/e1/resource/"
    private val chapters = (1..3).map { EpubChapter("${base}ch$it.html", null) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        books.chapters = chapters
        // 리소스 로더는 이 테스트에서 쓰지 않는다 (WebView 경로)
        val loader = EpubResourceLoader(ServerSessionManager(NoServerPreferences))
        viewModel = EpubViewModel(books, prefs, loader)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val reading get() = viewModel.uiState.value as EpubUiState.Reading

    @Test
    fun `저장된 자리의 장과 위치에서 연다 (Komga는 책 안 경로로 돌려준다)`() = runTest(dispatcher) {
        books.position = EpubPosition("ch2.html", progression = 0.4f, totalProgression = 0.45f)
        viewModel.loadBook("e1")
        advanceUntilIdle()

        assertEquals(1, reading.chapterIndex)
        assertEquals(0.4f, reading.startProgression)
    }

    @Test
    fun `다 읽은 책과 장을 못 찾는 자리는 처음부터`() = runTest(dispatcher) {
        books.position = EpubPosition("ch3.html", 0.9f, 0.95f)
        books.completed = true
        viewModel.loadBook("e1")
        advanceUntilIdle()
        assertEquals(0, reading.chapterIndex)

        books.completed = false
        books.position = EpubPosition("gone.html", 0.5f, 0.5f)
        viewModel = EpubViewModel(books, prefs, EpubResourceLoader(ServerSessionManager(NoServerPreferences)))
        viewModel.loadBook("e1")
        advanceUntilIdle()
        assertEquals(0, reading.chapterIndex)
        assertEquals(0f, reading.startProgression)
    }

    @Test
    fun `스크롤하면 2초 뒤 장과 위치를 저장하고, 전체 위치는 장 수로 나눈다`() = runTest(dispatcher) {
        viewModel.loadBook("e1")
        advanceUntilIdle()
        viewModel.nextChapter()
        viewModel.onScrolled(0.5f)

        advanceTimeBy(EpubViewModel.SAVE_DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertTrue(books.saved.isEmpty())
        advanceTimeBy(1)
        runCurrent()

        assertEquals(EpubPosition("${base}ch2.html", 0.5f, 0.5f), books.saved.single())
    }

    @Test
    fun `나갈 때 기다리던 저장을 바로 한다`() = runTest(dispatcher) {
        viewModel.loadBook("e1")
        advanceUntilIdle()
        viewModel.onScrolled(0.3f)

        viewModel.saveOnExit()
        runCurrent()

        assertEquals(0.3f, books.saved.single().progression)
    }

    @Test
    fun `이전 장으로 위로 넘어가면 그 장 끝에서, 장 범위를 넘지 않는다`() = runTest(dispatcher) {
        viewModel.loadBook("e1")
        advanceUntilIdle()
        viewModel.previousChapter()
        assertEquals(0, reading.chapterIndex)

        viewModel.nextChapter()
        viewModel.previousChapter(fromTop = false)
        assertEquals(0, reading.chapterIndex)
        assertEquals(1f, reading.startProgression)
    }

    @Test
    fun `글자 크기는 10씩 60~200 사이로 바뀌고 기본값으로 저장된다`() = runTest(dispatcher) {
        viewModel.loadBook("e1")
        advanceUntilIdle()
        viewModel.changeTextZoom(+1)
        advanceUntilIdle()
        assertEquals(110, reading.textZoom)
        assertEquals(110, prefs.settings.value.epubTextZoom)

        repeat(30) { viewModel.changeTextZoom(-1) }
        assertEquals(EpubViewModel.MIN_TEXT_ZOOM, reading.textZoom)
    }

    @Test
    fun `목차 항목으로 가면 그 장 처음에서 열고 목차와 오버레이를 닫는다`() = runTest(dispatcher) {
        books.toc = listOf(EpubTocEntry("1부", "ch1.html", 0), EpubTocEntry("3장", "ch3.html#top", 1))
        viewModel.loadBook("e1")
        advanceUntilIdle()
        viewModel.toggleOverlay()
        viewModel.openToc()
        assertTrue(reading.showToc)

        viewModel.jumpToToc(reading.toc[1])

        assertEquals(2, reading.chapterIndex)
        assertEquals(0f, reading.startProgression)
        assertEquals(false, reading.showToc)
        assertEquals(false, reading.showOverlay)
    }

    @Test
    fun `목차가 없거나 한 줄뿐인 책은 장마다 한 줄, 지금 장의 줄을 찾는다`() = runTest(dispatcher) {
        books.toc = listOf(EpubTocEntry("Start", "ch1.html", 0))
        viewModel.loadBook("e1")
        advanceUntilIdle()
        assertEquals(3, reading.toc.size)

        val toc = listOf(EpubTocEntry("1부", "ch1.html", 0), EpubTocEntry("1부 2", "ch1.html#b", 1), EpubTocEntry("3장", "ch3.html", 0))
        // 2장은 목차에 없다 — 그 앞 장(1장)을 가리키는 마지막 줄
        assertEquals(1, currentTocIndex(toc, chapters, chapterIndex = 1))
        assertEquals(2, currentTocIndex(toc, chapters, chapterIndex = 2))
    }

    @Test
    fun `책 제목이 파일 해시면 시리즈 제목을 쓴다`() = runTest(dispatcher) {
        val book = books.getBookById("e1")
        assertEquals("시리즈", book.copy(name = "a67879a1d4464860c3eedc980bd12118").displayName())
        assertEquals("1권", book.displayName())
    }

    @Test
    fun `같은 EPUB 파일인지는 서버 주소와 조각을 빼고 비교한다`() {
        assertTrue(sameResource("${base}OEBPS/ch1.html#p3", "OEBPS/ch1.html"))
    }

    private class FakeBookRepository : BookRepository {
        var chapters = listOf<EpubChapter>()
        var position: EpubPosition? = null
        var completed = false
        val saved = mutableListOf<EpubPosition>()

        override suspend fun getBookById(bookId: String) = Book(
            id = bookId, seriesId = "s", seriesTitle = "시리즈", name = "1권", number = 1, pageCount = 0,
            metadata = BookMetadata("1권", "", "1"),
            readProgress = if (completed) ReadProgress(1, true) else null,
            mediaProfile = MediaProfile.EPUB,
        )

        var toc = listOf<EpubTocEntry>()

        override suspend fun getEpubManifest(bookId: String) = EpubManifest(chapters, toc)
        override suspend fun getEpubPosition(bookId: String) = position
        override suspend fun saveEpubPosition(bookId: String, position: EpubPosition) {
            saved += position
        }

        override fun getBooks(seriesId: String, page: Int, size: Int): Flow<BookPageResult> = throw NotImplementedError()
        override fun getBooksInProgress(page: Int, size: Int): Flow<BookPageResult> = throw NotImplementedError()
        override suspend fun getBookPages(bookId: String): List<BookPage> = throw NotImplementedError()
        override suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean) = Unit
        override suspend fun getBooksOnDeck(size: Int): List<Book> = emptyList()
        override suspend fun getLatestBooks(size: Int): List<Book> = emptyList()
        override fun searchBooks(query: String, page: Int, size: Int): Flow<BookPageResult> = throw NotImplementedError()

        override suspend fun markRead(bookId: String) = Unit

        override suspend fun markUnread(bookId: String) = Unit

        override suspend fun analyze(bookId: String) = Unit
    }

    private class FakeReaderPreferences : ReaderPreferences {
        val settings = MutableStateFlow(ReaderSettings())
        override val readerSettings: Flow<ReaderSettings> = settings
        override suspend fun saveReaderSettings(settings: ReaderSettings) {
            this.settings.value = settings
        }
        override suspend fun saveEpubTextZoom(zoom: Int) {
            settings.value = settings.value.copy(epubTextZoom = zoom)
        }
        override fun seriesSettings(seriesId: String): Flow<ReaderSettings?> = MutableStateFlow(null)
        override suspend fun saveSeriesSettings(seriesId: String, settings: ReaderSettings) = Unit
        override suspend fun clearSeriesSettings(seriesId: String) = Unit
    }

    /** 연결 없는 서버 설정 (리소스 로더를 만들기만 하고 쓰지 않는다) */
    private object NoServerPreferences : ServerPreferences {
        override val serverConfig: Flow<ServerConfig> = MutableStateFlow(ServerConfig())
        override val serverUrl: Flow<String> = MutableStateFlow("")
        override val authConfig: Flow<AuthConfig> = MutableStateFlow(AuthConfig())
        override val authMethod: Flow<String> = MutableStateFlow("basic")
        override val defaultLibraryId: Flow<String> = MutableStateFlow("")
        override val savedServers: Flow<List<SavedServer>> = MutableStateFlow(emptyList())
        override suspend fun saveServerUrl(url: String) = Unit
        override suspend fun saveBasicAuth(username: String, password: String) = Unit
        override suspend fun saveApiKey(apiKey: String) = Unit
        override suspend fun saveDefaultLibrary(libraryId: String) = Unit
        override suspend fun clearAll() = Unit
        override suspend fun switchTo(serverId: String) = false
        override suspend fun removeServer(serverId: String) = Unit
    }
}
