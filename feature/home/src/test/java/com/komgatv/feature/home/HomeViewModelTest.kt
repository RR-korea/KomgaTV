package com.komgatv.feature.home

import com.komgatv.core.model.EpubPosition
import com.komgatv.core.model.EpubManifest
import com.komgatv.core.model.LoadError
import com.komgatv.core.data.repository.BookPageResult
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.LibraryRepository
import com.komgatv.core.data.repository.ReadProgressEvents
import com.komgatv.core.data.repository.SeriesPageResult
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookMetadata
import com.komgatv.core.model.BookPage
import com.komgatv.core.model.Library
import com.komgatv.core.model.ReadProgress
import com.komgatv.core.model.Series
import com.komgatv.core.model.SeriesMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeLibraryRepository: FakeLibraryRepository
    private lateinit var fakeBookRepository: FakeBookRepository
    private val progressEvents = ReadProgressEvents()
    private val fakeSeriesRepository = FakeSeriesRepository()

    @Before
    fun setUp() {
        fakeLibraryRepository = FakeLibraryRepository()
        fakeBookRepository = FakeBookRepository()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            libraryRepository = fakeLibraryRepository,
            bookRepository = fakeBookRepository,
            seriesRepository = fakeSeriesRepository,
            progressEvents = progressEvents,
            imageUrls = FakeImageUrls,
        )
    }

    @Test
    fun `라이브러리 스캔과 휴지통 비우기를 서버에 보내고 결과를 알린다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.scanLibrary("lib-1")
        advanceUntilIdle()
        assertEquals(LibraryActionResult.ScanRequested, viewModel.libraryMessage.value)

        viewModel.emptyTrash("lib-1")
        advanceUntilIdle()
        assertEquals(LibraryActionResult.TrashEmptied, viewModel.libraryMessage.value)
        assertEquals(listOf("scan:lib-1", "trash:lib-1"), fakeLibraryRepository.actions)
    }

    @Test
    fun `다음 권과 최근 추가된 시리즈 줄을 함께 읽는다`() = runTest {
        val book = Book(
            id = "next-1", seriesId = "s1", seriesTitle = "원피스", name = "원피스 2권", number = 2,
            pageCount = 100, metadata = BookMetadata("원피스 2권", "", "2"), readProgress = null,
        )
        fakeBookRepository.onDeckToReturn = listOf(book)
        fakeSeriesRepository.newSeriesToReturn = listOf(
            Series("s9", "lib", "새 시리즈", 3, 0, 0, SeriesMetadata("새 시리즈", "", "ONGOING", "", emptyList(), null)),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Success
        assertEquals(listOf("next-1"), state.onDeck.map { it.id })
        assertEquals(listOf("s9"), state.newSeries.map { it.id })
    }

    @Test
    fun `선택 줄이 실패해도 홈은 열리고 그 줄만 비어 있다`() = runTest {
        fakeBookRepository.failOnDeck = true

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Success
        assertTrue(state.onDeck.isEmpty())
    }

    @Test
    fun `진행 저장 알림이 오면 읽는 중 줄을 다시 읽는다`() = runTest {
        val book = Book(
            id = "book-1", seriesId = "series-1", seriesTitle = "원피스", name = "원피스 1권", number = 1,
            pageCount = 100, metadata = BookMetadata("원피스 1권", "", "1"),
            readProgress = ReadProgress(page = 50, completed = false),
        )
        fakeBookRepository.inProgressBooksToReturn = listOf(book)
        val viewModel = createViewModel()
        advanceUntilIdle()

        fakeBookRepository.inProgressBooksToReturn = listOf(book.copy(readProgress = ReadProgress(page = 60, completed = false)))
        progressEvents.notifyUpdated("book-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Success
        assertEquals(60, state.continueReading.single().readProgress?.page)
    }

    @Test
    fun `loadHomeData 성공 시 라이브러리 및 읽는 중인 책 목록이 Success 상태로 전이된다`() = runTest {
        val testLibrary = Library(id = "lib-1", name = "만화")
        val testBook = Book(
            id = "book-1",
            seriesId = "series-1",
            seriesTitle = "원피스",
            name = "원피스 1권",
            number = 1,
            pageCount = 100,
            metadata = BookMetadata("원피스 1권", "", "1"),
            readProgress = ReadProgress(page = 50, completed = false),
        )

        fakeLibraryRepository.librariesToReturn = listOf(testLibrary)
        fakeBookRepository.inProgressBooksToReturn = listOf(testBook)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue("Success 상태여야 함", state is HomeUiState.Success)
        val successState = state as HomeUiState.Success
        assertEquals(1, successState.libraries.size)
        assertEquals("만화", successState.libraries.first().name)
        assertEquals(1, successState.continueReading.size)
        assertEquals("원피스 1권", successState.continueReading.first().name)
        assertEquals("http://example.com:8080/api/v1/books/b1/thumbnail", viewModel.imageUrls.bookThumbnail("b1"))
    }

    @Test
    fun `라이브러리 로드 실패 시 Error 상태로 전이된다`() = runTest {
        fakeLibraryRepository.shouldFail = true

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue("Error 상태여야 함", state is HomeUiState.Error)
        val errorState = state as HomeUiState.Error
        assertEquals(LoadError.UNKNOWN, errorState.error)
    }

    @Test
    fun `읽는 중인 책 목록 로드 실패 시 Error 상태로 전이된다`() = runTest {
        fakeBookRepository.shouldFail = true

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue("Error 상태여야 함", state is HomeUiState.Error)
        val errorState = state as HomeUiState.Error
        assertEquals(LoadError.UNKNOWN, errorState.error)
    }

    @Test
    fun `오류 발생 후 refresh 호출 시 성공 상태로 복구된다`() = runTest {
        fakeLibraryRepository.shouldFail = true
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value is HomeUiState.Error)

        // 에러 해제 후 refresh
        fakeLibraryRepository.shouldFail = false
        fakeLibraryRepository.librariesToReturn = listOf(Library("lib-1", "만화"))
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertTrue("Success 상태로 복구되어야 함", state is HomeUiState.Success)
    }

    private class FakeLibraryRepository : LibraryRepository {
        var librariesToReturn = listOf<Library>()
        var shouldFail = false

        override fun getLibraries(): Flow<List<Library>> = flow {
            if (shouldFail) throw RuntimeException("라이브러리 네트워크 에러")
            emit(librariesToReturn)
        }

        override suspend fun getLibraryById(libraryId: String): Library {
            if (shouldFail) throw RuntimeException("라이브러리 조회 실패")
            return librariesToReturn.firstOrNull { it.id == libraryId }
                ?: Library(libraryId, "가짜 라이브러리")
        }

        val actions = mutableListOf<String>()

        override suspend fun scan(libraryId: String) {
            actions += "scan:$libraryId"
        }

        override suspend fun emptyTrash(libraryId: String) {
            actions += "trash:$libraryId"
        }
    }

    private class FakeBookRepository : BookRepository {
        var inProgressBooksToReturn = listOf<Book>()
        var shouldFail = false

        override fun getBooks(seriesId: String, page: Int, size: Int): Flow<BookPageResult> = flow {
            emit(BookPageResult(emptyList(), 0, 0, 0, true))
        }

        override fun getBooksInProgress(page: Int, size: Int): Flow<BookPageResult> = flow {
            if (shouldFail) throw RuntimeException("도서 조회 실패")
            emit(
                BookPageResult(
                    books = inProgressBooksToReturn,
                    totalPages = 1,
                    totalElements = inProgressBooksToReturn.size.toLong(),
                    currentPage = page,
                    isLast = true,
                )
            )
        }

        override suspend fun getBookById(bookId: String): Book {
            throw NotImplementedError()
        }

        override suspend fun getBookPages(bookId: String): List<BookPage> {
            throw NotImplementedError()
        }

        override suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean) {
        }

        var onDeckToReturn = listOf<Book>()
        var failOnDeck = false

        override suspend fun getBooksOnDeck(size: Int): List<Book> {
            if (failOnDeck) throw RuntimeException("on deck 실패")
            return onDeckToReturn
        }

        override suspend fun getLatestBooks(size: Int): List<Book> = emptyList()

        override fun searchBooks(query: String, page: Int, size: Int): Flow<BookPageResult> = throw NotImplementedError()

        override suspend fun getEpubManifest(bookId: String): EpubManifest = throw NotImplementedError()

        override suspend fun getEpubPosition(bookId: String): EpubPosition? = null

        override suspend fun saveEpubPosition(bookId: String, position: EpubPosition) = Unit

        override suspend fun markRead(bookId: String) = Unit

        override suspend fun markUnread(bookId: String) = Unit

        override suspend fun analyze(bookId: String) = Unit
    }

    private class FakeSeriesRepository : SeriesRepository {
        var newSeriesToReturn = listOf<Series>()

        override fun getSeries(page: Int, size: Int, libraryId: String?): Flow<SeriesPageResult> = throw NotImplementedError()

        override suspend fun getSeriesById(seriesId: String): Series = throw NotImplementedError()

        override fun searchSeries(query: String, page: Int, size: Int): Flow<SeriesPageResult> = throw NotImplementedError()

        override suspend fun getNewSeries(size: Int): List<Series> = newSeriesToReturn

        override suspend fun getUpdatedSeries(size: Int): List<Series> = emptyList()
    }
}
