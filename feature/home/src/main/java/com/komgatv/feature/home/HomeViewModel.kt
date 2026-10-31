package com.komgatv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.error.toLoadError
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.LibraryRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.Library
import com.komgatv.core.model.LoadError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

    /** 에러 발생 — 화면이 [error] 종류를 현재 언어 문구로 보여준다 */
    data class Error(val error: LoadError) : HomeUiState
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
    val imageUrls: ImageUrls,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()


    init {
        loadHomeData()
    }

    /**
     * 홈 화면 데이터 로드
     */
    fun loadHomeData() {
        viewModelScope.launch {
            // 이미 내용이 보이면 로딩 화면으로 바꾸지 않는다 (포커스·스크롤 위치 유지)
            val showing = _uiState.value as? HomeUiState.Success
            if (showing == null) _uiState.value = HomeUiState.Loading

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "홈 데이터 로드 실패")
                // 보이는 내용이 있으면 그대로 두고, 처음 읽기에 실패했을 때만 오류 화면을 보인다
                if (showing != null) return@launch
                _uiState.value = HomeUiState.Error(e.toLoadError())
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

    /** 데이터 새로고침 (다른 화면에서 돌아왔을 때 — 읽기 진행이 바뀌었을 수 있다) */
    fun refresh() {
        loadHomeData()
    }
}
