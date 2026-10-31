package com.komgatv.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.repository.LibraryRepository
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Series
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 라이브러리 화면 UI 상태
 */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Success(
        val libraryName: String,
        val series: List<Series>,
        val isLoadingMore: Boolean = false,
        val currentPage: Int = 0,
        val isLastPage: Boolean = false,
    ) : LibraryUiState

    data class Error(val message: String) : LibraryUiState
}

/**
 * 라이브러리 화면 ViewModel
 *
 * 특정 라이브러리의 시리즈 목록을 페이지네이션으로 관리합니다.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val seriesRepository: SeriesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var currentLibraryId: String = ""
    private val PAGE_SIZE = 30

    /**
     * 라이브러리 데이터 로드
     *
     * @param libraryId 라이브러리 ID
     */
    fun loadLibrary(libraryId: String) {
        if (currentLibraryId == libraryId && _uiState.value is LibraryUiState.Success) {
            return // 이미 로드된 데이터가 있으면 재로드하지 않음
        }

        currentLibraryId = libraryId
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading

            try {
                // 라이브러리 이름 조회
                val library = libraryRepository.getLibraryById(libraryId)
                val libraryName = library?.name ?: "라이브러리"

                // 첫 페이지 시리즈 로드
                val result = seriesRepository.getSeries(
                    page = 0,
                    size = PAGE_SIZE,
                    libraryId = libraryId,
                ).catch { e ->
                    Timber.e(e, "시리즈 로드 실패")
                    throw e
                }.first()

                _uiState.value = LibraryUiState.Success(
                    libraryName = libraryName,
                    series = result.series,
                    currentPage = result.currentPage,
                    isLastPage = result.isLast,
                )
            } catch (e: Exception) {
                Timber.e(e, "라이브러리 로드 실패: $libraryId")
                _uiState.value = LibraryUiState.Error(
                    message = e.localizedMessage ?: "라이브러리를 불러올 수 없습니다"
                )
            }
        }
    }

    /**
     * 추가 시리즈 로드 (페이지네이션)
     *
     * 현재 마지막 페이지가 아닌 경우에만 다음 페이지를 로드합니다.
     */
    fun loadMoreSeries() {
        val currentState = _uiState.value
        if (currentState !is LibraryUiState.Success) return
        if (currentState.isLastPage || currentState.isLoadingMore) return

        viewModelScope.launch {
            // 로딩 상태 업데이트
            _uiState.value = currentState.copy(isLoadingMore = true)

            try {
                val nextPage = currentState.currentPage + 1
                val result = seriesRepository.getSeries(
                    page = nextPage,
                    size = PAGE_SIZE,
                    libraryId = currentLibraryId,
                ).first()

                _uiState.value = currentState.copy(
                    series = currentState.series + result.series,
                    isLoadingMore = false,
                    currentPage = result.currentPage,
                    isLastPage = result.isLast,
                )
            } catch (e: Exception) {
                Timber.e(e, "추가 시리즈 로드 실패")
                _uiState.value = currentState.copy(isLoadingMore = false)
            }
        }
    }
}
