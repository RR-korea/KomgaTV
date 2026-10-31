package com.komgatv.feature.library

import com.komgatv.core.data.repository.LibraryRepository
import com.komgatv.core.data.repository.SeriesPageResult
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Library
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.Series
import com.komgatv.core.model.SeriesMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeLibraryRepository = FakeLibraryRepository()
    private val fakeSeriesRepository = FakeSeriesRepository()

    private fun createViewModel(): LibraryViewModel {
        return LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            seriesRepository = fakeSeriesRepository,
            imageUrls = FakeImageUrls,
        )
    }

    private fun createSeries(id: String, name: String) = Series(
        id = id,
        libraryId = "lib-1",
        name = name,
        booksCount = 10,
        booksReadCount = 0,
        booksInProgressCount = 0,
        metadata = SeriesMetadata(name, "", "ONGOING", "출판사", emptyList(), ReadingDirection.RIGHT_TO_LEFT),
    )

    @Test
    fun `loadLibrary 성공 시 첫 페이지 시리즈가 Success 상태로 설정된다`() = runTest {
        val seriesPage0 = (1..50).map { createSeries("series-$it", "시리즈 $it") }
        fakeSeriesRepository.pages[0] = SeriesPageResult(
            series = seriesPage0,
            totalPages = 2,
            totalElements = 100,
            currentPage = 0,
            isLast = false,
        )

        val viewModel = createViewModel()
        viewModel.loadLibrary("lib-1")

        val state = viewModel.uiState.value
        assertTrue("Success 상태여야 함", state is LibraryUiState.Success)
        val successState = state as LibraryUiState.Success
        assertEquals("테스트 라이브러리", successState.libraryName)
        assertEquals(50, successState.series.size)
        assertEquals(0, successState.currentPage)
        assertFalse(successState.isLastPage)
        assertFalse(successState.isLoadingMore)
    }

    @Test
    fun `loadMoreSeries 호출 시 다음 페이지 시리즈가 누적되어 로드된다`() = runTest {
        val seriesPage0 = (1..50).map { createSeries("series-$it", "시리즈 $it") }
        val seriesPage1 = (51..70).map { createSeries("series-$it", "시리즈 $it") }

        fakeSeriesRepository.pages[0] = SeriesPageResult(
            series = seriesPage0,
            totalPages = 2,
            totalElements = 70,
            currentPage = 0,
            isLast = false,
        )
        fakeSeriesRepository.pages[1] = SeriesPageResult(
            series = seriesPage1,
            totalPages = 2,
            totalElements = 70,
            currentPage = 1,
            isLast = true,
        )

        val viewModel = createViewModel()
        viewModel.loadLibrary("lib-1")

        // 페이지네이션 다음 페이지 로드
        viewModel.loadMoreSeries()

        val state = viewModel.uiState.value
        assertTrue("Success 상태여야 함", state is LibraryUiState.Success)
        val successState = state as LibraryUiState.Success
        assertEquals(70, successState.series.size)
        assertEquals(1, successState.currentPage)
        assertTrue("마지막 페이지여야 함", successState.isLastPage)
        assertFalse(successState.isLoadingMore)
    }

    @Test
    fun `라이브러리 로드 실패 시 Error 상태로 전이된다`() = runTest {
        fakeLibraryRepository.shouldFail = true

        val viewModel = createViewModel()
        viewModel.loadLibrary("lib-1")

        val state = viewModel.uiState.value
        assertTrue("Error 상태여야 함", state is LibraryUiState.Error)
        val errorState = state as LibraryUiState.Error
        assertEquals("라이브러리 조회 실패", errorState.message)
    }

    @Test
    fun `추가 페이지 로드 실패 시 기존 목록이 유지되고 isLoadingMore가 false로 복구된다`() = runTest {
        val seriesPage0 = (1..50).map { createSeries("series-$it", "시리즈 $it") }
        fakeSeriesRepository.pages[0] = SeriesPageResult(
            series = seriesPage0,
            totalPages = 2,
            totalElements = 100,
            currentPage = 0,
            isLast = false,
        )

        val viewModel = createViewModel()
        viewModel.loadLibrary("lib-1")
        assertTrue(viewModel.uiState.value is LibraryUiState.Success)

        // 다음 페이지 실패 설정
        fakeSeriesRepository.shouldFailOnPage1 = true
        viewModel.loadMoreSeries()

        val state = viewModel.uiState.value
        assertTrue("Success 상태가 유지되어야 함", state is LibraryUiState.Success)
        val successState = state as LibraryUiState.Success
        assertEquals(50, successState.series.size)
        assertFalse("isLoadingMore는 false로 복구되어야 함", successState.isLoadingMore)
    }

    private class FakeLibraryRepository : LibraryRepository {
        var shouldFail = false

        override fun getLibraries(): Flow<List<Library>> = flow {
            emit(listOf(Library("lib-1", "테스트 라이브러리")))
        }

        override suspend fun getLibraryById(libraryId: String): Library {
            if (shouldFail) throw RuntimeException("라이브러리 조회 실패")
            return Library(libraryId, "테스트 라이브러리")
        }
    }

    private class FakeSeriesRepository : SeriesRepository {
        val pages = mutableMapOf<Int, SeriesPageResult>()
        var shouldFailOnPage1 = false

        override fun getSeries(page: Int, size: Int, libraryId: String?): Flow<SeriesPageResult> = flow {
            if (page == 1 && shouldFailOnPage1) {
                throw RuntimeException("추가 페이지 로드 실패")
            }
            emit(pages[page] ?: SeriesPageResult(emptyList(), 0, 0, page, true))
        }

        override suspend fun getSeriesById(seriesId: String): Series {
            throw NotImplementedError()
        }

        override fun searchSeries(query: String, page: Int, size: Int): Flow<SeriesPageResult> = flow {
            emit(SeriesPageResult(emptyList(), 0, 0, page, true))
        }
    }
}
