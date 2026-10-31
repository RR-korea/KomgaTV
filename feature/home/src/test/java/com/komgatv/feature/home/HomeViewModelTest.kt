package com.komgatv.feature.home

import com.komgatv.core.model.LoadError
import com.komgatv.core.data.repository.BookPageResult
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.LibraryRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookMetadata
import com.komgatv.core.model.Library
import com.komgatv.core.model.ReadProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    @Before
    fun setUp() {
        fakeLibraryRepository = FakeLibraryRepository()
        fakeBookRepository = FakeBookRepository()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            libraryRepository = fakeLibraryRepository,
            bookRepository = fakeBookRepository,
            imageUrls = FakeImageUrls,
        )
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

        override suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean) {
        }
    }
}
