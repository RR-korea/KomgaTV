package com.komgatv.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.model.Book
import com.komgatv.core.network.ServerSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 리더 화면 UI 상태
 */
sealed interface ReaderUiState {

    /** 책 데이터 로딩 중 */
    data object Loading : ReaderUiState

    /**
     * 읽기 상태
     *
     * @property bookTitle 책 제목
     * @property currentPage 현재 페이지 번호 (1-based)
     * @property totalPages 전체 페이지 수
     * @property currentPageUrl 현재 페이지 이미지 URL (세션 기준 절대 URL)
     * @property nextPageUrl 다음 페이지 이미지 URL (프리로드용, null이면 마지막 페이지)
     * @property showOverlay 정보 오버레이 표시 여부
     */
    data class Reading(
        val bookTitle: String,
        val currentPage: Int,
        val totalPages: Int,
        val currentPageUrl: String,
        val nextPageUrl: String?,
        val showOverlay: Boolean = false,
    ) : ReaderUiState

    /** 에러 상태 */
    data class Error(val message: String) : ReaderUiState
}

/**
 * 리더 화면 ViewModel
 *
 * 페이지 상태 관리, 다음 페이지 프리로딩, 읽기 진행 상태 동기화를 담당합니다.
 * 3-페이지 규칙(이전/현재/다음)에 따라 메모리를 관리합니다.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val sessionManager: ServerSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBook: Book? = null
    private var currentBookId: String = ""
    private var hasProgressChanged = false

    /**
     * 책 데이터 로드
     *
     * 책 정보를 가져오고, 이전에 읽던 위치가 있으면 해당 페이지에서 시작합니다.
     *
     * @param bookId 책 ID
     */
    fun loadBook(bookId: String) {
        if (currentBookId == bookId && _uiState.value is ReaderUiState.Reading) {
            return
        }

        currentBookId = bookId
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading

            try {
                val book = bookRepository.getBookById(bookId)
                currentBook = book

                // 이전 읽기 진행이 있으면 해당 페이지에서 시작
                val startPage = book.readProgress?.page?.coerceIn(1, book.pageCount) ?: 1

                updateReadingState(
                    bookTitle = book.metadata.title.ifEmpty { book.name },
                    currentPage = startPage,
                    totalPages = book.pageCount,
                )
            } catch (e: Exception) {
                Timber.e(e, "책 로드 실패: $bookId")
                _uiState.value = ReaderUiState.Error(
                    message = e.localizedMessage ?: "책을 불러올 수 없습니다"
                )
            }
        }
    }

    /** 다음 페이지로 이동 */
    fun nextPage() {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        if (state.currentPage >= state.totalPages) return // 마지막 페이지

        hasProgressChanged = true
        updateReadingState(
            bookTitle = state.bookTitle,
            currentPage = state.currentPage + 1,
            totalPages = state.totalPages,
        )
    }

    /** 이전 페이지로 이동 */
    fun previousPage() {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        if (state.currentPage <= 1) return // 첫 페이지

        hasProgressChanged = true
        updateReadingState(
            bookTitle = state.bookTitle,
            currentPage = state.currentPage - 1,
            totalPages = state.totalPages,
        )
    }

    /** 정보 오버레이 토글 */
    fun toggleOverlay() {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        _uiState.value = state.copy(showOverlay = !state.showOverlay)
    }

    /**
     * 리더 종료 시 진행 상태 저장
     *
     * 현재 페이지 위치와 완독 여부를 서버에 동기화합니다.
     */
    fun saveProgressOnExit() {
        if (!hasProgressChanged) return

        val state = _uiState.value as? ReaderUiState.Reading ?: return

        viewModelScope.launch {
            try {
                val completed = state.currentPage >= state.totalPages
                bookRepository.updateReadProgress(
                    bookId = currentBookId,
                    page = state.currentPage,
                    completed = completed,
                )
                Timber.d("읽기 진행 저장 완료: $currentBookId, 페이지 ${state.currentPage}/${state.totalPages}")
            } catch (e: Exception) {
                Timber.e(e, "읽기 진행 저장 실패: $currentBookId")
            }
        }
    }

    /**
     * 읽기 상태 업데이트
     *
     * 현재 페이지와 프리로드할 다음 페이지 URL을 함께 설정합니다.
     */
    private fun updateReadingState(
        bookTitle: String,
        currentPage: Int,
        totalPages: Int,
    ) {
        val currentPageUrl = buildPageUrl(currentBookId, currentPage)
        val nextPageUrl = if (currentPage < totalPages) {
            buildPageUrl(currentBookId, currentPage + 1)
        } else {
            null
        }

        val existingState = _uiState.value as? ReaderUiState.Reading

        _uiState.value = ReaderUiState.Reading(
            bookTitle = bookTitle,
            currentPage = currentPage,
            totalPages = totalPages,
            currentPageUrl = currentPageUrl,
            nextPageUrl = nextPageUrl,
            showOverlay = existingState?.showOverlay ?: false,
        )
    }

    /**
     * 페이지 이미지 절대 URL 생성 (F2 해결)
     */
    private fun buildPageUrl(bookId: String, pageNumber: Int): String {
        val baseUrl = sessionManager.session.value?.baseUrl ?: ""
        return if (baseUrl.isNotBlank()) {
            "${baseUrl.trimEnd('/')}/api/v1/books/$bookId/pages/$pageNumber"
        } else {
            ""
        }
    }
}
