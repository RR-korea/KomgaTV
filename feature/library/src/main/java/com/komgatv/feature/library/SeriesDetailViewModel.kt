package com.komgatv.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.Series
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 시리즈 상세 화면 UI 상태
 */
sealed interface SeriesDetailUiState {
    data object Loading : SeriesDetailUiState

    data class Success(
        val series: Series,
        val books: List<Book>,
        val isLoadingMore: Boolean = false,
        val currentPage: Int = 0,
        val isLastPage: Boolean = false,
    ) : SeriesDetailUiState

    data class Error(val message: String) : SeriesDetailUiState
}

/**
 * 시리즈 상세 화면 ViewModel
 *
 * 시리즈의 상세 메타데이터와 권별 도서 목록 및 읽기 진행률을 관리합니다.
 */
@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val bookRepository: BookRepository,
    val imageUrls: ImageUrls,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SeriesDetailUiState>(SeriesDetailUiState.Loading)
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()


    private var currentSeriesId: String = ""

    companion object {
        const val PAGE_SIZE = 50
    }

    /**
     * 시리즈 상세 정보 및 도서 목록 로드
     *
     * @param seriesId 시리즈 ID
     */
    fun loadSeriesDetail(seriesId: String) {
        if (currentSeriesId == seriesId && _uiState.value is SeriesDetailUiState.Success) {
            return
        }

        currentSeriesId = seriesId
        viewModelScope.launch {
            _uiState.value = SeriesDetailUiState.Loading

            try {
                // 시리즈 정보 조회
                val series = seriesRepository.getSeriesById(seriesId)

                // 첫 페이지 도서 목록 로드 (페이지 크기 50)
                val bookResult = bookRepository.getBooks(
                    seriesId = seriesId,
                    page = 0,
                    size = PAGE_SIZE,
                ).first()

                _uiState.value = SeriesDetailUiState.Success(
                    series = series,
                    books = bookResult.books,
                    currentPage = bookResult.currentPage,
                    isLastPage = bookResult.isLast,
                )
            } catch (e: Exception) {
                Timber.e(e, "시리즈 상세 로드 실패: $seriesId")
                _uiState.value = SeriesDetailUiState.Error(
                    message = e.localizedMessage ?: "시리즈 정보를 불러올 수 없습니다"
                )
            }
        }
    }

    /**
     * 추가 도서 목록 로드 (무한 스크롤 페이지네이션)
     */
    fun loadMoreBooks() {
        val currentState = _uiState.value
        if (currentState !is SeriesDetailUiState.Success) return
        if (currentState.isLastPage || currentState.isLoadingMore) return

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoadingMore = true)

            try {
                val nextPage = currentState.currentPage + 1
                val result = bookRepository.getBooks(
                    seriesId = currentSeriesId,
                    page = nextPage,
                    size = PAGE_SIZE,
                ).first()

                _uiState.value = currentState.copy(
                    books = currentState.books + result.books,
                    isLoadingMore = false,
                    currentPage = result.currentPage,
                    isLastPage = result.isLast,
                )
            } catch (e: Exception) {
                Timber.e(e, "추가 도서 로드 실패")
                _uiState.value = currentState.copy(isLoadingMore = false)
            }
        }
    }
}
