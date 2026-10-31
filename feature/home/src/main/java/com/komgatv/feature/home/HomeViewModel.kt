package com.komgatv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.LibraryRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.Library
import com.komgatv.core.network.ServerSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * @property continueReading 읽는 중인 책 목록 (read status: IN_PROGRESS, 최근 읽은 순)
     * @property libraries 라이브러리 목록
     */
    data class Success(
        val continueReading: List<Book> = emptyList(),
        val libraries: List<Library> = emptyList(),
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
 * MVI 패턴에 기반하여 라이브러리 목록과 읽는 중인 책 목록을 병렬로 로드하고 UI 상태를 관리합니다.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
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
                // coroutineScope로 묶어 자식 비동기 작업의 실패가 structured concurrency로 바깥 try-catch에 안전하게 전달되도록 함
                coroutineScope {
                    val librariesDeferred = async { loadLibraries() }
                    val continueReadingDeferred = async { loadContinueReading() }

                    val libraries = librariesDeferred.await()
                    val continueReading = continueReadingDeferred.await()

                    _uiState.value = HomeUiState.Success(
                        continueReading = continueReading,
                        libraries = libraries,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "홈 데이터 로드 실패")
                _uiState.value = HomeUiState.Error(
                    message = e.localizedMessage ?: "홈 화면 데이터를 불러올 수 없습니다"
                )
            }
        }
    }

    /** 라이브러리 목록 로드 */
    private suspend fun loadLibraries(): List<Library> {
        return libraryRepository.getLibraries().first()
    }

    /** 읽는 중인 책 목록 로드 (IN_PROGRESS, 최근 읽은 순) */
    private suspend fun loadContinueReading(): List<Book> {
        return bookRepository.getBooksInProgress(page = 0, size = 20).first().books
    }

    /** 데이터 새로고침 */
    fun refresh() {
        loadHomeData()
    }
}
