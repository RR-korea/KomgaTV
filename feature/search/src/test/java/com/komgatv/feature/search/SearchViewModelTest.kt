package com.komgatv.feature.search

import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.BookPageResult
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.SeriesPageResult
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookMetadata
import com.komgatv.core.model.BookPage
import com.komgatv.core.model.LoadError
import com.komgatv.core.model.Series
import com.komgatv.core.model.SeriesMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSeriesRepository()
    private val bookRepository = FakeBookRepository()
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SearchViewModel(repository, bookRepository, FakeImageUrls)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `입력이 멈춘 뒤 마지막 검색어로 한 번만 검색한다`() = runTest(dispatcher) {
        repository.results["원피스"] = listOf(series("s1", "원피스"))

        viewModel.onQueryChange("원")
        advanceTimeBy(200)
        viewModel.onQueryChange("원피")
        advanceTimeBy(200)
        viewModel.onQueryChange("원피스")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertEquals(emptyList<String>(), repository.queries)

        advanceUntilIdle()
        assertEquals(listOf("원피스"), repository.queries)
        assertEquals(listOf("s1"), (viewModel.uiState.value as SearchUiState.Results).series.items.map { it.id })
    }

    @Test
    fun `앞뒤 공백만 다르면 다시 검색하지 않고, 지우면 처음 상태로`() = runTest(dispatcher) {
        repository.results["킹덤"] = listOf(series("s1", "킹덤"))
        viewModel.onQueryChange("킹덤")
        advanceUntilIdle()
        viewModel.onQueryChange(" 킹덤 ")
        advanceUntilIdle()
        assertEquals(listOf("킹덤"), repository.queries)

        viewModel.onQueryChange("")
        advanceUntilIdle()
        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `시리즈도 책도 없으면 Empty`() = runTest(dispatcher) {
        viewModel.onQueryChange("없는제목")
        advanceUntilIdle()

        assertEquals(SearchUiState.Empty("없는제목"), viewModel.uiState.value)
    }

    @Test
    fun `검색이 실패하면 오류 종류를 내보낸다`() = runTest(dispatcher) {
        repository.fail = true
        viewModel.onQueryChange("킹덤")
        advanceUntilIdle()

        assertEquals(SearchUiState.Error(LoadError.UNKNOWN), viewModel.uiState.value)
    }

    @Test
    fun `다음 페이지를 이어 붙인다`() = runTest(dispatcher) {
        repository.pageSize = 2
        repository.results["바키"] = (1..3).map { series("s$it", "바키 $it") }
        viewModel.onQueryChange("바키")
        advanceUntilIdle()
        assertEquals(2, (viewModel.uiState.value as SearchUiState.Results).series.items.size)

        viewModel.loadMoreSeries()
        advanceUntilIdle()

        val state = viewModel.uiState.value as SearchUiState.Results
        assertEquals(listOf("s1", "s2", "s3"), state.series.items.map { it.id })
        assertEquals(true, state.series.isLastPage)
    }

    @Test
    fun `책 제목도 함께 찾고, 책만 맞아도 결과로 보인다`() = runTest(dispatcher) {
        bookRepository.results["유자룡"] = listOf(book("b1", "유자룡의 과거"))
        viewModel.onQueryChange("유자룡")
        advanceUntilIdle()

        val state = viewModel.uiState.value as SearchUiState.Results
        assertEquals(emptyList<String>(), state.series.items.map { it.id })
        assertEquals(listOf("b1"), state.books.items.map { it.id })
    }

    @Test
    fun `책 줄도 다음 페이지를 이어 붙인다`() = runTest(dispatcher) {
        bookRepository.pageSize = 2
        bookRepository.results["바키"] = (1..3).map { book("b$it", "바키 $it") }
        viewModel.onQueryChange("바키")
        advanceUntilIdle()

        viewModel.loadMoreBooks()
        advanceUntilIdle()

        val books = (viewModel.uiState.value as SearchUiState.Results).books
        assertEquals(listOf("b1", "b2", "b3"), books.items.map { it.id })
        assertEquals(3L, books.total)
    }

    private fun series(id: String, name: String) = Series(
        id = id,
        libraryId = "lib",
        name = name,
        booksCount = 1,
        booksReadCount = 0,
        booksInProgressCount = 0,
        metadata = SeriesMetadata(name, "", "ONGOING", "", emptyList(), null),
    )

    private fun book(id: String, name: String) = Book(
        id = id,
        seriesId = "s",
        seriesTitle = "시리즈",
        name = name,
        number = 1,
        pageCount = 10,
        metadata = BookMetadata(name, "", "1"),
        readProgress = null,
    )

    private class FakeBookRepository : BookRepository {
        val results = mutableMapOf<String, List<Book>>()
        var pageSize = 50

        override fun searchBooks(query: String, page: Int, size: Int): Flow<BookPageResult> = flow {
            val all = results[query].orEmpty()
            val chunk = all.drop(page * pageSize).take(pageSize)
            val totalPages = maxOf(1, (all.size + pageSize - 1) / pageSize)
            emit(BookPageResult(chunk, totalPages, all.size.toLong(), page, page >= totalPages - 1))
        }

        override fun getBooks(seriesId: String, page: Int, size: Int): Flow<BookPageResult> = throw NotImplementedError()
        override fun getBooksInProgress(page: Int, size: Int): Flow<BookPageResult> = throw NotImplementedError()
        override suspend fun getBookById(bookId: String): Book = throw NotImplementedError()
        override suspend fun getBookPages(bookId: String): List<BookPage> = throw NotImplementedError()
        override suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean) = Unit
        override suspend fun getBooksOnDeck(size: Int): List<Book> = emptyList()
        override suspend fun getLatestBooks(size: Int): List<Book> = emptyList()
    }

    private class FakeSeriesRepository : SeriesRepository {
        val results = mutableMapOf<String, List<Series>>()
        val queries = mutableListOf<String>()
        var fail = false
        var pageSize = 50

        override fun getSeries(page: Int, size: Int, libraryId: String?): Flow<SeriesPageResult> = throw NotImplementedError()

        override suspend fun getSeriesById(seriesId: String): Series = throw NotImplementedError()

        override fun searchSeries(query: String, page: Int, size: Int): Flow<SeriesPageResult> = flow {
            if (page == 0) queries += query
            if (fail) throw RuntimeException("server down")
            val all = results[query].orEmpty()
            val chunk = all.drop(page * pageSize).take(pageSize)
            val totalPages = (all.size + pageSize - 1) / pageSize
            emit(SeriesPageResult(chunk, totalPages, all.size.toLong(), page, page >= totalPages - 1))
        }

        override suspend fun getNewSeries(size: Int): List<Series> = emptyList()

        override suspend fun getUpdatedSeries(size: Int): List<Series> = emptyList()
    }

    private object FakeImageUrls : ImageUrls {
        override fun seriesThumbnail(seriesId: String) = "http://localhost/series/$seriesId"
        override fun bookThumbnail(bookId: String) = "http://localhost/books/$bookId"
        override fun page(bookId: String, pageNumber: Int) = "http://localhost/books/$bookId/pages/$pageNumber"
    }
}
