package com.komgatv.feature.reader

import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.BookPageResult
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.SeriesPageResult
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.datastore.ReaderPreferences
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookMetadata
import com.komgatv.core.model.BookPage
import com.komgatv.core.model.LoadError
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.ReadProgress
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import com.komgatv.core.model.Series
import com.komgatv.core.model.SeriesMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ReaderViewModel 단위 테스트
 *
 * 검증 항목:
 * 1. 방향 우선순위: 시리즈 메타데이터 > 저장된 기본값
 * 2. 방향 변경 시 즉시 반영 및 전역 기본값 저장
 * 3. D-pad 좌/우 이동 및 읽기 방향에 따른 반전
 * 4. 두 쪽 펼침에서 진행 저장 (보이는 쪽 중 큰 번호 저장)
 * 5. 마지막 페이지 펼침 도달 시 완독(completed=true) 저장
 * 6. 서버 진행도 페이지가 포함된 펼침에서 시작 (이어 읽기)
 * 7. 2초 디바운스 및 종료 시 즉시 저장 (saveProgressOnExit)
 * 8. 펼침 기준 앞뒤 2개 프리로드 URL 생성
 * 9. 설정 변경 (쪽 배치, 밀기, 크기 맞춤, 작은 이미지 키우기) 및 저장
 * 10. 10페이지 점프 조작
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bookRepository: FakeBookRepository
    private lateinit var seriesRepository: FakeSeriesRepository
    private lateinit var readerPreferences: FakeReaderPreferences
    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        bookRepository = FakeBookRepository()
        seriesRepository = FakeSeriesRepository()
        readerPreferences = FakeReaderPreferences()
        viewModel = ReaderViewModel(bookRepository, seriesRepository, FakeImageUrls, readerPreferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `이어 읽기 - 서버에 저장된 진행도 페이지가 속한 펼침에서 시작한다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "series1", direction = ReadingDirection.LEFT_TO_RIGHT)
        val book = createTestBook(
            id = "book1",
            seriesId = "series1",
            pageCount = 20,
            progress = ReadProgress(page = 7, completed = false),
        )
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..20).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertTrue(state.currentSpread.containsPage(7))
        assertEquals(20, state.totalPages)
    }

    @Test
    fun `방향 우선순위 - 시리즈에 방향이 있으면 기본값보다 시리즈 방향을 우선한다`() = runTest(testDispatcher) {
        // 기본 설정은 LEFT_TO_RIGHT
        readerPreferences.saveReadingDirection(ReadingDirection.LEFT_TO_RIGHT)

        // 시리즈는 RIGHT_TO_LEFT
        val series = createTestSeries(id = "s_rtl", direction = ReadingDirection.RIGHT_TO_LEFT)
        val book = createTestBook(id = "b1", seriesId = "s_rtl", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, state.readingDirection)
    }

    @Test
    fun `방향 우선순위 - 시리즈에 방향이 없으면 저장된 기본값을 사용한다`() = runTest(testDispatcher) {
        readerPreferences.saveReadingDirection(ReadingDirection.RIGHT_TO_LEFT)

        // 시리즈에 방향 지정 안 됨 (null)
        val series = createTestSeries(id = "s_null", direction = null)
        val book = createTestBook(id = "b1", seriesId = "s_null", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, state.readingDirection)
    }

    @Test
    fun `방향 우선순위 - 시리즈가 WEBTOON이면 페이지 리더는 저장된 기본값을 쓴다`() = runTest(testDispatcher) {
        readerPreferences.saveReadingDirection(ReadingDirection.RIGHT_TO_LEFT)
        val series = createTestSeries(id = "s_webtoon", direction = ReadingDirection.WEBTOON)
        val book = createTestBook(id = "b1", seriesId = "s_webtoon", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, state.readingDirection)
    }

    @Test
    fun `로드 실패 - 책이 없으면 오류 종류를 내보낸다`() = runTest(testDispatcher) {
        viewModel.loadBook("missing")
        advanceUntilIdle()

        assertEquals(ReaderUiState.Error(LoadError.UNKNOWN), viewModel.uiState.value)
    }

    @Test
    fun `방향 변경 - 리더에서 방향을 바꾸면 지금 책에 바로 적용되고 기본값으로 저장된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1", direction = ReadingDirection.LEFT_TO_RIGHT)
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ReadingDirection.LEFT_TO_RIGHT, state.readingDirection)

        // 방향 변경
        viewModel.setReadingDirection(ReadingDirection.RIGHT_TO_LEFT)
        advanceUntilIdle()

        // 지금 책에 즉시 반영
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, state.readingDirection)

        // 기본값으로도 저장됨
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, readerPreferences.readerSettings.first().readingDirection)
    }

    @Test
    fun `방향 반전 - RIGHT_TO_LEFT 시리즈는 D-pad 좌측이 다음 펼침, 우측이 이전 펼침`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "series_rtl", direction = ReadingDirection.RIGHT_TO_LEFT)
        val book = createTestBook(id = "book_rtl", seriesId = "series_rtl", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(0, state.currentSpreadIndex)

        // D-pad 좌측 누름 -> 다음 펼침(index 1)으로 이동
        viewModel.onDpadLeft()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(1, state.currentSpreadIndex)

        // D-pad 우측 누름 -> 이전 펼침(index 0)으로 이동
        viewModel.onDpadRight()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(0, state.currentSpreadIndex)
    }

    @Test
    fun `기본 방향 - LEFT_TO_RIGHT 시리즈는 D-pad 우측이 다음 펼침, 좌측이 이전 펼침`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "series_ltr", direction = ReadingDirection.LEFT_TO_RIGHT)
        val book = createTestBook(id = "book_ltr", seriesId = "series_ltr", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(0, state.currentSpreadIndex)

        // D-pad 우측 누름 -> 다음 펼침(index 1)으로 이동
        viewModel.onDpadRight()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(1, state.currentSpreadIndex)

        // D-pad 좌측 누름 -> 이전 펼침(index 0)으로 이동
        viewModel.onDpadLeft()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(0, state.currentSpreadIndex)
    }

    @Test
    fun `두 쪽 보기 진행 저장 - 펼침에서 보이는 쪽 중 큰 번호를 저장한다`() = runTest(testDispatcher) {
        readerPreferences.savePageLayout(PageLayout.DOUBLE_PAGES)
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..10).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        // DOUBLE_PAGES: 펼침 0은 [1], 펼침 1은 [2, 3]
        viewModel.nextSpread() // 펼침 1 [2, 3]으로 이동
        advanceTimeBy(2000L)
        advanceUntilIdle()

        assertEquals(1, bookRepository.progressUpdates.size)
        val update = bookRepository.progressUpdates.first()
        assertEquals("b1", update.bookId)
        // [2, 3] 중 큰 번호인 3이 저장되어야 함
        assertEquals(3, update.page)
        assertFalse(update.completed)
    }

    @Test
    fun `완독 - 마지막 쪽이 보이는 펼침에 도달하면 completed=true로 저장된다`() = runTest(testDispatcher) {
        readerPreferences.savePageLayout(PageLayout.DOUBLE_PAGES_NO_COVER)
        val series = createTestSeries(id = "s1")
        // 4페이지 책: 펼침 0=[1, 2], 펼침 1=[3, 4]
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 4)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..4).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.nextSpread() // [3, 4] 펼침으로 이동 (마지막 쪽 4 포함)
        advanceTimeBy(2000L)
        advanceUntilIdle()

        assertEquals(1, bookRepository.progressUpdates.size)
        val update = bookRepository.progressUpdates.first()
        assertEquals(4, update.page)
        assertTrue(update.completed)
    }

    @Test
    fun `디바운스 - 연속으로 빠르게 넘기면 마지막 페이지만 1회 저장된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.nextSpread()
        advanceTimeBy(500L)
        runCurrent()
        viewModel.nextSpread()
        advanceTimeBy(500L)
        runCurrent()
        viewModel.nextSpread()
        runCurrent()

        // 아직 2초 미경과
        advanceTimeBy(1500L)
        runCurrent()
        assertTrue(bookRepository.progressUpdates.isEmpty())

        // 2초 경과 (추가 500ms)
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(1, bookRepository.progressUpdates.size)
        assertEquals(4, bookRepository.progressUpdates.first().page)
    }

    @Test
    fun `종료 시 즉시 저장 - 2초 미경과 상태에서 saveProgressOnExit 호출 시 즉시 저장된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.nextSpread()
        advanceTimeBy(300L)
        assertTrue(bookRepository.progressUpdates.isEmpty())

        viewModel.saveProgressOnExit()
        advanceUntilIdle()

        assertEquals(1, bookRepository.progressUpdates.size)
        assertEquals(2, bookRepository.progressUpdates.first().page)
    }

    @Test
    fun `프리로드 - 펼침 기준 앞뒤 2개 펼침의 모든 페이지가 프리로드 URL로 생성된다`() = runTest(testDispatcher) {
        // DOUBLE_PAGES_NO_COVER: [1, 2], [3, 4], [5, 6], [7, 8], [9, 10]
        readerPreferences.savePageLayout(PageLayout.DOUBLE_PAGES_NO_COVER)
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10, progress = ReadProgress(page = 5, completed = false))
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..10).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        // 현재 펼침: index 2 ([5, 6])
        assertEquals(2, state.currentSpreadIndex)
        // 앞뒤 2개 펼침 인덱스: 0([1, 2]), 1([3, 4]), 3([7, 8]), 4([9, 10])
        val url = { n: Int -> "http://localhost:8080/api/v1/books/b1/pages/$n" }
        val expected = listOf(listOf(url(1), url(2)), listOf(url(3), url(4)), listOf(url(7), url(8)), listOf(url(9), url(10)))
        assertEquals(expected, state.preloadSpreads)
    }

    @Test
    fun `설정 변경 - 쪽 배치 변경 시 보고 있던 페이지를 유지하면서 펼침 재계산 및 저장된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10, progress = ReadProgress(page = 5, completed = false))
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..10).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        // 현재 SINGLE_PAGE 상태에서 5페이지 보고 있음
        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(PageLayout.SINGLE_PAGE, state.pageLayout)
        assertTrue(state.currentSpread.containsPage(5))

        // 두 쪽 보기로 변경
        viewModel.setPageLayout(PageLayout.DOUBLE_PAGES)
        advanceUntilIdle()

        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(PageLayout.DOUBLE_PAGES, state.pageLayout)
        // 여전히 5페이지가 포함된 펼침이어야 함
        assertTrue(state.currentSpread.containsPage(5))
        assertEquals(PageLayout.DOUBLE_PAGES, readerPreferences.readerSettings.first().pageLayout)
    }

    @Test
    fun `설정 변경 - 밀기(offset) 토글 시 펼침이 재계산된다`() = runTest(testDispatcher) {
        readerPreferences.savePageLayout(PageLayout.DOUBLE_PAGES)
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 6)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..6).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertFalse(state.layoutOffset)
        assertEquals(4, state.totalSpreads) // [1], [2, 3], [4, 5], [6]

        // 밀기 켜기
        viewModel.setLayoutOffset(true)
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertTrue(state.layoutOffset)
        // [1], [2], [3, 4], [5, 6] -> 4개 펼침
        assertEquals(4, state.totalSpreads)
    }

    @Test
    fun `설정 변경 - 크기 맞춤 및 작은 이미지 키우기 설정 저장`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 6)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.setScaleType(ScaleType.FIT_WIDTH)
        viewModel.setStretchToFit(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(ScaleType.FIT_WIDTH, state.scaleType)
        assertFalse(state.stretchToFit)
        assertEquals(ScaleType.FIT_WIDTH, readerPreferences.readerSettings.first().scaleType)
        assertFalse(readerPreferences.readerSettings.first().stretchToFit)
    }

    @Test
    fun `점프 - 10페이지 점프 조작 시 범위 내에서 정상 이동한다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 25, progress = ReadProgress(page = 5, completed = false))
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book
        bookRepository.pagesMap[book.id] = (1..25).map { BookPage(number = it) }

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        // 10페이지 앞으로 (+10)
        viewModel.jumpPages(10)
        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(15, state.currentSpread.pages.first().number)

        // 10페이지 뒤로 (-10)
        viewModel.jumpPages(-10)
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(5, state.currentSpread.pages.first().number)

        // 최소값 1로 제한
        viewModel.jumpPages(-10)
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(1, state.currentSpread.pages.first().number)
    }

    private fun createTestSeries(id: String, direction: ReadingDirection? = null) = Series(
        id = id,
        libraryId = "lib1",
        name = "Test Series",
        booksCount = 1,
        booksReadCount = 0,
        booksInProgressCount = 0,
        metadata = SeriesMetadata(
            title = "Test Series",
            summary = "",
            status = "ONGOING",
            publisher = "",
            genres = emptyList(),
            readingDirection = direction,
        ),
    )

    private fun createTestBook(
        id: String,
        seriesId: String,
        pageCount: Int,
        progress: ReadProgress? = null,
    ) = Book(
        id = id,
        seriesId = seriesId,
        seriesTitle = "Test Series",
        name = "Chapter 1",
        number = 1,
        pageCount = pageCount,
        metadata = BookMetadata(
            title = "Chapter 1",
            summary = "",
            number = "1",
        ),
        readProgress = progress,
    )

    private data class ProgressUpdate(val bookId: String, val page: Int, val completed: Boolean)

    private class FakeBookRepository : BookRepository {
        val books = mutableMapOf<String, Book>()
        val pagesMap = mutableMapOf<String, List<BookPage>>()
        val progressUpdates = mutableListOf<ProgressUpdate>()

        override fun getBooks(seriesId: String, page: Int, size: Int): Flow<BookPageResult> {
            throw NotImplementedError()
        }

        override fun getBooksInProgress(page: Int, size: Int): Flow<BookPageResult> {
            throw NotImplementedError()
        }

        override suspend fun getBookById(bookId: String): Book {
            return books[bookId] ?: throw NoSuchElementException("Book not found: $bookId")
        }

        override suspend fun getBookPages(bookId: String): List<BookPage> {
            return pagesMap[bookId] ?: (1..(books[bookId]?.pageCount ?: 1)).map { BookPage(number = it) }
        }

        override suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean) {
            progressUpdates.add(ProgressUpdate(bookId, page, completed))
        }
    }

    private class FakeSeriesRepository : SeriesRepository {
        val seriesMap = mutableMapOf<String, Series>()

        override fun getSeries(page: Int, size: Int, libraryId: String?): Flow<SeriesPageResult> {
            throw NotImplementedError()
        }

        override suspend fun getSeriesById(seriesId: String): Series {
            return seriesMap[seriesId] ?: throw NoSuchElementException("Series not found: $seriesId")
        }

        override fun searchSeries(query: String, page: Int, size: Int): Flow<SeriesPageResult> {
            throw NotImplementedError()
        }
    }

    private class FakeReaderPreferences(
        initialSettings: ReaderSettings = ReaderSettings(),
    ) : ReaderPreferences {
        private val _settings = MutableStateFlow(initialSettings)
        override val readerSettings: Flow<ReaderSettings> = _settings.asStateFlow()


        override suspend fun savePageLayout(layout: PageLayout) {
            _settings.value = _settings.value.copy(pageLayout = layout)
        }

        override suspend fun saveScaleType(scaleType: ScaleType) {
            _settings.value = _settings.value.copy(scaleType = scaleType)
        }

        override suspend fun saveStretchToFit(stretch: Boolean) {
            _settings.value = _settings.value.copy(stretchToFit = stretch)
        }

        override suspend fun saveReadingDirection(direction: ReadingDirection) {
            _settings.value = _settings.value.copy(readingDirection = direction)
        }
    }

    private object FakeImageUrls : ImageUrls {
        private const val BASE = "http://localhost:8080"
        override fun seriesThumbnail(seriesId: String) = "$BASE/api/v1/series/$seriesId/thumbnail"
        override fun bookThumbnail(bookId: String) = "$BASE/api/v1/books/$bookId/thumbnail"
        override fun page(bookId: String, pageNumber: Int) = "$BASE/api/v1/books/$bookId/pages/$pageNumber"
    }
}
