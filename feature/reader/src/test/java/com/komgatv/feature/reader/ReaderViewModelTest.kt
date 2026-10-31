package com.komgatv.feature.reader

import com.komgatv.core.data.repository.BookPageResult
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.SeriesPageResult
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookMetadata
import com.komgatv.core.model.ReadProgress
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.Series
import com.komgatv.core.model.SeriesMetadata
import com.komgatv.core.network.ServerSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
 * 1. 읽기 방향(ReadingDirection)에 따른 D-pad 좌/우 반전
 * 2. 2초 디바운스 진행 저장 및 연속 이동 시 최종 페이지 1회 저장
 * 3. 완독(completed=true) 및 중간 진행(completed=false) 저장
 * 4. 서버 진행도 페이지에서 시작 (이어 읽기)
 * 5. 종료 시 즉시 저장 (saveProgressOnExit)
 * 6. 이웃 페이지(±2) 프리로드 URL 생성
 * 7. 10페이지 점프 조작
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bookRepository: FakeBookRepository
    private lateinit var seriesRepository: FakeSeriesRepository
    private lateinit var sessionManager: ServerSessionManager
    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        bookRepository = FakeBookRepository()
        seriesRepository = FakeSeriesRepository()
        sessionManager = ServerSessionManager("http://localhost:8080")
        viewModel = ReaderViewModel(bookRepository, seriesRepository, sessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `이어 읽기 - 서버에 저장된 진행도 페이지에서 시작한다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "series1", direction = ReadingDirection.LEFT_TO_RIGHT)
        val book = createTestBook(
            id = "book1",
            seriesId = "series1",
            pageCount = 20,
            progress = ReadProgress(page = 7, completed = false),
        )
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(7, state.currentPage)
        assertEquals(20, state.totalPages)
    }

    @Test
    fun `방향 반전 - RIGHT_TO_LEFT 시리즈는 D-pad 좌측이 다음 페이지, 우측이 이전 페이지`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "series_rtl", direction = ReadingDirection.RIGHT_TO_LEFT)
        val book = createTestBook(
            id = "book_rtl",
            seriesId = "series_rtl",
            pageCount = 10,
            progress = null,
        )
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(1, state.currentPage)

        // D-pad 좌측 누름 -> 다음 페이지(2)로 이동
        viewModel.onDpadLeft()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(2, state.currentPage)

        // D-pad 우측 누름 -> 이전 페이지(1)로 이동
        viewModel.onDpadRight()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(1, state.currentPage)
    }

    @Test
    fun `기본 방향 - LEFT_TO_RIGHT 시리즈는 D-pad 우측이 다음 페이지, 좌측이 이전 페이지`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "series_ltr", direction = ReadingDirection.LEFT_TO_RIGHT)
        val book = createTestBook(
            id = "book_ltr",
            seriesId = "series_ltr",
            pageCount = 10,
            progress = null,
        )
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(1, state.currentPage)

        // D-pad 우측 누름 -> 다음 페이지(2)로 이동
        viewModel.onDpadRight()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(2, state.currentPage)

        // D-pad 좌측 누름 -> 이전 페이지(1)로 이동
        viewModel.onDpadLeft()
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(1, state.currentPage)
    }

    @Test
    fun `디바운스 - 페이지 이동 후 2초 경과 시 서버에 진행도가 저장된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.nextPage() // 1 -> 2

        // 1초 경과 시점에는 아직 저장되지 않아야 함
        advanceTimeBy(1000L)
        runCurrent()
        assertTrue(bookRepository.progressUpdates.isEmpty())

        // 추가 1000ms 경과 (총 2000ms) 시 저장 호출
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(1, bookRepository.progressUpdates.size)
        val update = bookRepository.progressUpdates.first()
        assertEquals("b1", update.bookId)
        assertEquals(2, update.page)
        assertFalse(update.completed)
    }

    @Test
    fun `디바운스 - 연속으로 빠르게 페이지를 넘기면 마지막 페이지만 1회 저장된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.nextPage() // 2페이지
        advanceTimeBy(500L)
        runCurrent()
        viewModel.nextPage() // 3페이지
        advanceTimeBy(500L)
        runCurrent()
        viewModel.nextPage() // 4페이지
        runCurrent()

        // 아직 2초가 지나지 않음 (마지막 이동 후 1500ms)
        advanceTimeBy(1500L)
        runCurrent()
        assertTrue(bookRepository.progressUpdates.isEmpty())

        // 마지막 이동 후 2초 경과 (추가 500ms, 총 2000ms)
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(1, bookRepository.progressUpdates.size)
        val update = bookRepository.progressUpdates.first()
        assertEquals(4, update.page)
    }

    @Test
    fun `완독 - 마지막 페이지에 도달하면 completed=true로 저장된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 5, progress = ReadProgress(page = 4, completed = false))
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.nextPage() // 4 -> 5 (마지막 페이지)
        advanceTimeBy(2000L)
        advanceUntilIdle()

        assertEquals(1, bookRepository.progressUpdates.size)
        val update = bookRepository.progressUpdates.first()
        assertEquals(5, update.page)
        assertTrue(update.completed)
    }

    @Test
    fun `종료 시 즉시 저장 - 2초가 지나기 전에도 saveProgressOnExit 호출 시 즉시 동기화된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10)
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        viewModel.nextPage() // 2페이지
        advanceTimeBy(300L)
        assertTrue(bookRepository.progressUpdates.isEmpty())

        // 화면 종료 호출
        viewModel.saveProgressOnExit()
        advanceUntilIdle()

        assertEquals(1, bookRepository.progressUpdates.size)
        assertEquals(2, bookRepository.progressUpdates.first().page)
    }

    @Test
    fun `프리로드 - 현재 페이지 기준 ±2 범위 내 페이지만 프리로드 URL로 생성된다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 10, progress = ReadProgress(page = 5, completed = false))
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals("http://localhost:8080/api/v1/books/b1/pages/5", state.currentPageUrl)
        assertEquals(
            listOf(
                "http://localhost:8080/api/v1/books/b1/pages/3",
                "http://localhost:8080/api/v1/books/b1/pages/4",
                "http://localhost:8080/api/v1/books/b1/pages/6",
                "http://localhost:8080/api/v1/books/b1/pages/7",
            ),
            state.preloadUrls,
        )
    }

    @Test
    fun `점프 - 10페이지 점프 조작 시 범위 내에서 정상 이동한다`() = runTest(testDispatcher) {
        val series = createTestSeries(id = "s1")
        val book = createTestBook(id = "b1", seriesId = "s1", pageCount = 25, progress = ReadProgress(page = 5, completed = false))
        seriesRepository.seriesMap[series.id] = series
        bookRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        advanceUntilIdle()

        // 10페이지 앞으로 (+10)
        viewModel.jumpForward(10)
        var state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(15, state.currentPage)

        // 10페이지 뒤로 (-10)
        viewModel.jumpBackward(10)
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(5, state.currentPage)

        // 경계값 테스트: 5에서 -10 점프 시 최소값 1로 제한
        viewModel.jumpBackward(10)
        state = viewModel.uiState.value as ReaderUiState.Reading
        assertEquals(1, state.currentPage)
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
        val progressUpdates = mutableListOf<ProgressUpdate>()

        override fun getBooks(seriesId: String, page: Int, size: Int): Flow<BookPageResult> {
            throw NotImplementedError()
        }

        override suspend fun getBookById(bookId: String): Book {
            return books[bookId] ?: throw NoSuchElementException("Book not found: $bookId")
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
}
