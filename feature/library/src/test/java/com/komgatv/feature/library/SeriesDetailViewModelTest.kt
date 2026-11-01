package com.komgatv.feature.library

import com.komgatv.core.model.EpubPosition
import com.komgatv.core.model.EpubManifest
import com.komgatv.core.model.LoadError
import com.komgatv.core.data.repository.BookPageResult
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.ReadProgressEvents
import com.komgatv.core.data.repository.SeriesPageResult
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookMetadata
import com.komgatv.core.model.BookPage
import com.komgatv.core.model.ReadProgress
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.Series
import com.komgatv.core.model.SeriesMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeSeriesRepository = FakeSeriesRepository()
    private val fakeBookRepository = FakeBookRepository()
    private val progressEvents = ReadProgressEvents()

    private fun createViewModel(): SeriesDetailViewModel {
        return SeriesDetailViewModel(
            seriesRepository = fakeSeriesRepository,
            bookRepository = fakeBookRepository,
            progressEvents = progressEvents,
            imageUrls = FakeImageUrls,
        )
    }

    private fun createTestBook(id: String, number: Int) = Book(
        id = id,
        seriesId = "series-1",
        seriesTitle = "테스트 시리즈",
        name = "${number}권",
        number = number,
        pageCount = 100,
        metadata = BookMetadata("${number}권", "", number.toString()),
        readProgress = if (number == 1) ReadProgress(page = 100, completed = true) else null,
    )

    @Test
    fun `loadSeriesDetail 성공 시 시리즈 상세 및 첫 페이지 도서 목록이 Success 상태로 설정된다`() = runTest {
        val testSeries = Series(
            id = "series-1",
            libraryId = "lib-1",
            name = "테스트 시리즈",
            booksCount = 70,
            booksReadCount = 1,
            booksInProgressCount = 0,
            metadata = SeriesMetadata(
                title = "테스트 시리즈",
                summary = "줄거리",
                status = "ONGOING",
                publisher = "출판사",
                genres = listOf("액션"),
                readingDirection = ReadingDirection.RIGHT_TO_LEFT,
            ),
        )
        fakeSeriesRepository.seriesToReturn = testSeries

        val booksPage0 = (1..50).map { createTestBook("book-$it", it) }
        fakeBookRepository.pages[0] = BookPageResult(
            books = booksPage0,
            totalPages = 2,
            totalElements = 70,
            currentPage = 0,
            isLast = false,
        )

        val viewModel = createViewModel()
        viewModel.loadSeriesDetail("series-1")

        val state = viewModel.uiState.value
        assertTrue("Success 상태여야 함", state is SeriesDetailUiState.Success)
        val successState = state as SeriesDetailUiState.Success
        assertEquals("테스트 시리즈", successState.series.name)
        assertEquals(50, successState.books.size)
        assertEquals(0, successState.currentPage)
        assertFalse(successState.isLastPage)
        assertFalse(successState.isLoadingMore)
    }

    @Test
    fun `이 시리즈 책의 진행 저장 알림이 오면 진행 표시를 다시 읽는다`() = runTest {
        fakeSeriesRepository.seriesToReturn = Series(
            id = "series-1", libraryId = "lib-1", name = "테스트 시리즈", booksCount = 2,
            booksReadCount = 0, booksInProgressCount = 0,
            metadata = SeriesMetadata("테스트 시리즈", "", "ENDED", "", emptyList(), null),
        )
        val books = listOf(createTestBook("book-1", 1), createTestBook("book-2", 2))
        fakeBookRepository.pages[0] = BookPageResult(books, 1, 2, 0, true)
        val viewModel = createViewModel()
        viewModel.loadSeriesDetail("series-1")
        advanceUntilIdle()

        val updated = books.map { if (it.id == "book-2") it.copy(readProgress = ReadProgress(page = 7, completed = false)) else it }
        fakeBookRepository.pages[0] = BookPageResult(updated, 1, 2, 0, true)
        progressEvents.notifyUpdated("other-series-book")
        advanceUntilIdle()
        assertEquals(null, (viewModel.uiState.value as SeriesDetailUiState.Success).books[1].readProgress)

        progressEvents.notifyUpdated("book-2")
        advanceUntilIdle()
        assertEquals(7, (viewModel.uiState.value as SeriesDetailUiState.Success).books[1].readProgress?.page)
    }

    @Test
    fun `책 메뉴 - 읽지 않음·분석은 서버에 보내고 결과를 알린다, 실패하면 실패로`() = runTest {
        fakeSeriesRepository.seriesToReturn = Series(
            id = "series-1", libraryId = "lib-1", name = "시리즈", booksCount = 1,
            booksReadCount = 0, booksInProgressCount = 0,
            metadata = SeriesMetadata("시리즈", "", "ENDED", "", emptyList(), null),
        )
        fakeBookRepository.pages[0] = BookPageResult(listOf(createTestBook("book-1", 1)), 1, 1, 0, true)
        val viewModel = createViewModel()
        viewModel.loadSeriesDetail("series-1")
        advanceUntilIdle()

        viewModel.markUnread("book-1")
        advanceUntilIdle()
        assertEquals(BookActionResult.MarkedUnread, viewModel.actionMessage.value)

        viewModel.analyze("book-1")
        advanceUntilIdle()
        assertEquals(listOf("unread:book-1", "analyze:book-1"), fakeBookRepository.actions)
        assertEquals(BookActionResult.AnalyzeRequested, viewModel.actionMessage.value)

        fakeBookRepository.failAnalyze = true
        viewModel.analyze("book-1")
        advanceUntilIdle()
        assertEquals(BookActionResult.Failed, viewModel.actionMessage.value)
    }

    @Test
    fun `loadMoreBooks 호출 시 다음 페이지 도서가 누적되어 로드된다`() = runTest {
        val testSeries = Series(
            id = "series-1",
            libraryId = "lib-1",
            name = "테스트 시리즈",
            booksCount = 70,
            booksReadCount = 0,
            booksInProgressCount = 0,
            metadata = SeriesMetadata("테스트 시리즈", "", "ENDED", "", emptyList(), null),
        )
        fakeSeriesRepository.seriesToReturn = testSeries

        val booksPage0 = (1..50).map { createTestBook("book-$it", it) }
        val booksPage1 = (51..70).map { createTestBook("book-$it", it) }

        fakeBookRepository.pages[0] = BookPageResult(
            books = booksPage0,
            totalPages = 2,
            totalElements = 70,
            currentPage = 0,
            isLast = false,
        )
        fakeBookRepository.pages[1] = BookPageResult(
            books = booksPage1,
            totalPages = 2,
            totalElements = 70,
            currentPage = 1,
            isLast = true,
        )

        val viewModel = createViewModel()
        viewModel.loadSeriesDetail("series-1")

        // 페이지네이션 다음 페이지 로드
        viewModel.loadMoreBooks()

        val state = viewModel.uiState.value
        assertTrue("Success 상태여야 함", state is SeriesDetailUiState.Success)
        val successState = state as SeriesDetailUiState.Success
        assertEquals(70, successState.books.size)
        assertEquals(1, successState.currentPage)
        assertTrue("마지막 페이지여야 함", successState.isLastPage)
        assertFalse(successState.isLoadingMore)
    }

    @Test
    fun `시리즈 상세 로드 실패 시 Error 상태로 전이된다`() = runTest {
        fakeSeriesRepository.shouldFail = true

        val viewModel = createViewModel()
        viewModel.loadSeriesDetail("series-1")

        val state = viewModel.uiState.value
        assertTrue("Error 상태여야 함", state is SeriesDetailUiState.Error)
        val errorState = state as SeriesDetailUiState.Error
        assertEquals(LoadError.UNKNOWN, errorState.error)
    }

    @Test
    fun `추가 도서 로드 실패 시 기존 도서 목록이 유지되고 isLoadingMore가 false로 복구된다`() = runTest {
        val testSeries = Series(
            id = "series-1",
            libraryId = "lib-1",
            name = "테스트 시리즈",
            booksCount = 70,
            booksReadCount = 0,
            booksInProgressCount = 0,
            metadata = SeriesMetadata("테스트 시리즈", "", "ENDED", "", emptyList(), null),
        )
        fakeSeriesRepository.seriesToReturn = testSeries

        val booksPage0 = (1..50).map { createTestBook("book-$it", it) }
        fakeBookRepository.pages[0] = BookPageResult(
            books = booksPage0,
            totalPages = 2,
            totalElements = 70,
            currentPage = 0,
            isLast = false,
        )

        val viewModel = createViewModel()
        viewModel.loadSeriesDetail("series-1")
        assertTrue(viewModel.uiState.value is SeriesDetailUiState.Success)

        // 다음 페이지 실패 설정
        fakeBookRepository.shouldFailOnPage1 = true
        viewModel.loadMoreBooks()

        val state = viewModel.uiState.value
        assertTrue("Success 상태가 유지되어야 함", state is SeriesDetailUiState.Success)
        val successState = state as SeriesDetailUiState.Success
        assertEquals(50, successState.books.size)
        assertFalse("isLoadingMore는 false로 복구되어야 함", successState.isLoadingMore)
    }

    private class FakeSeriesRepository : SeriesRepository {
        var seriesToReturn: Series? = null
        var shouldFail = false

        override fun getSeries(page: Int, size: Int, libraryId: String?): Flow<SeriesPageResult> = flow {
            emit(SeriesPageResult(emptyList(), 0, 0, page, true))
        }

        override suspend fun getSeriesById(seriesId: String): Series {
            if (shouldFail) throw RuntimeException("시리즈 정보 조회 실패")
            return seriesToReturn ?: throw RuntimeException("시리즈를 찾을 수 없음")
        }

        override fun searchSeries(query: String, page: Int, size: Int): Flow<SeriesPageResult> = flow {
            emit(SeriesPageResult(emptyList(), 0, 0, page, true))
        }

        override suspend fun getNewSeries(size: Int): List<Series> = emptyList()

        override suspend fun getUpdatedSeries(size: Int): List<Series> = emptyList()
    }

    private class FakeBookRepository : BookRepository {
        val pages = mutableMapOf<Int, BookPageResult>()
        var shouldFailOnPage1 = false

        override fun getBooks(seriesId: String, page: Int, size: Int): Flow<BookPageResult> = flow {
            if (page == 1 && shouldFailOnPage1) {
                throw RuntimeException("추가 도서 로드 실패")
            }
            emit(pages[page] ?: BookPageResult(emptyList(), 0, 0, page, true))
        }

        override fun getBooksInProgress(page: Int, size: Int): Flow<BookPageResult> = flow {
            emit(BookPageResult(emptyList(), 0, 0, page, true))
        }

        override suspend fun getBookById(bookId: String): Book {
            throw NotImplementedError()
        }

        override suspend fun getBookPages(bookId: String): List<BookPage> {
            throw NotImplementedError()
        }

        override suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean) {
        }

        override suspend fun getBooksOnDeck(size: Int): List<Book> = emptyList()

        override suspend fun getLatestBooks(size: Int): List<Book> = emptyList()

        override fun searchBooks(query: String, page: Int, size: Int): Flow<BookPageResult> = throw NotImplementedError()

        override suspend fun getEpubManifest(bookId: String): EpubManifest = throw NotImplementedError()

        override suspend fun getEpubPosition(bookId: String): EpubPosition? = null

        override suspend fun saveEpubPosition(bookId: String, position: EpubPosition) = Unit

        val actions = mutableListOf<String>()
        var failAnalyze = false

        override suspend fun markRead(bookId: String) {
            actions += "read:$bookId"
        }

        override suspend fun markUnread(bookId: String) {
            actions += "unread:$bookId"
        }

        override suspend fun analyze(bookId: String) {
            if (failAnalyze) throw RuntimeException("403")
            actions += "analyze:$bookId"
        }

        override suspend fun getNextBook(bookId: String): Book? = null
        override suspend fun getPreviousBook(bookId: String): Book? = null
    }
}
