package com.komgatv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.LibraryRepository
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.Library
import com.komgatv.core.model.Series
import com.komgatv.core.network.ServerSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 홈 화면 UI 상태 (MVI 패턴)
 */
sealed interface HomeUiState {

    /** 데이터 로딩 중 */
    data object Loading : HomeUiState

    /**
     * 데이터 로딩 성공
     *
     * @property continueReading 계속 읽기 목록 (진행 중인 책)
     * @property recentlyAdded 최근 추가된 시리즈
     * @property libraries 라이브러리 목록
     * @property featuredSeries 추천 시리즈 (히어로 배너용)
     */
    data class Success(
        val continueReading: List<Book> = emptyList(),
        val recentlyAdded: List<Series> = emptyList(),
        val libraries: List<Library> = emptyList(),
        val featuredSeries: List<Series> = emptyList(),
    ) : HomeUiState

    /**
     * 에러 발생
     *
     * @property message 에러 메시지
     */
    data class Error(val message: String) : HomeUiState
}

/**
 * 홈 화면 ViewModel
 *
 * MVI 패턴에 기반하여 홈 화면의 데이터를 로드하고 UI 상태를 관리합니다.
 * 계속 읽기, 최근 추가, 라이브러리 데이터를 병렬로 로드합니다.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val seriesRepository: SeriesRepository,
    private val bookRepository: BookRepository,
    private val sessionManager: ServerSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 현재 세션의 기본 URL */
    val baseUrl: String
        get() = sessionManager.session.value?.baseUrl ?: ""

    init {
        loadHomeData()
    }

    /**
     * 홈 화면 데이터 로드
     */
    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            try {
                // 병렬 데이터 로딩
                val librariesDeferred = async { loadLibraries() }
                val recentSeriesDeferred = async { loadRecentlyAdded() }
                val continueReadingDeferred = async { loadContinueReading() }

                val libraries = librariesDeferred.await()
                val recentSeries = recentSeriesDeferred.await()
                val continueReading = continueReadingDeferred.await()

                // 추천 시리즈: 최근 추가에서 상위 5개 선택
                val featuredSeries = recentSeries.take(5)

                _uiState.value = HomeUiState.Success(
                    continueReading = continueReading,
                    recentlyAdded = recentSeries,
                    libraries = libraries,
                    featuredSeries = featuredSeries,
                )
            } catch (e: Exception) {
                Timber.e(e, "홈 데이터 로드 실패")
                _uiState.value = HomeUiState.Error(
                    message = e.localizedMessage ?: "알 수 없는 오류가 발생했습니다"
                )
            }
        }
    }

    /** 라이브러리 목록 로드 */
    private suspend fun loadLibraries(): List<Library> {
        return try {
            libraryRepository.getLibraries()
                .catch { e ->
                    Timber.e(e, "라이브러리 로드 실패")
                    emit(emptyList())
                }
                .first()
        } catch (e: Exception) {
            Timber.e(e, "라이브러리 로드 예외")
            emptyList()
        }
    }

    /** 최근 추가된 시리즈 로드 */
    private suspend fun loadRecentlyAdded(): List<Series> {
        return try {
            seriesRepository.getSeries(page = 0, size = 20)
                .catch { e ->
                    Timber.e(e, "최근 시리즈 로드 실패")
                    emit(
                        com.komgatv.core.data.repository.SeriesPageResult(
                            series = emptyList(),
                            totalPages = 0,
                            totalElements = 0,
                            currentPage = 0,
                            isLast = true,
                        )
                    )
                }
                .first()
                .series
        } catch (e: Exception) {
            Timber.e(e, "최근 시리즈 로드 예외")
            emptyList()
        }
    }

    /** 계속 읽기 목록 로드 */
    private suspend fun loadContinueReading(): List<Book> {
        return emptyList()
    }

    /** 데이터 새로고침 */
    fun refresh() {
        loadHomeData()
    }
}
