package com.komgatv.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.ReadingDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
     * @property preloadUrls 프리로드 대상 페이지 이미지 URL 목록 (±2 페이지)
     * @property readingDirection 시리즈에 설정된 읽기 방향
     * @property showOverlay 정보 오버레이 표시 여부
     */
    data class Reading(
        val bookTitle: String,
        val currentPage: Int,
        val totalPages: Int,
        val currentPageUrl: String,
        val preloadUrls: List<String>,
        val readingDirection: ReadingDirection,
        val showOverlay: Boolean = false,
    ) : ReaderUiState

    /** 에러 상태 */
    data class Error(val message: String) : ReaderUiState
}

/**
 * 리더 화면 ViewModel
 *
 * 시리즈 읽기 방향에 따른 좌/우 반전, 프리로드(±2), 2초 디바운스 및 종료 시 진행 저장을 관리합니다.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val seriesRepository: SeriesRepository,
    private val imageUrls: ImageUrls,
) : ViewModel() {

    companion object {
        /** 진행 저장 디바운스 시간 (2초) */
        const val SAVE_DEBOUNCE_MILLIS = 2000L
    }

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBook: Book? = null
    private var currentBookId: String = ""
    private var currentReadingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT

    private var saveProgressJob: Job? = null
    private var hasProgressChanged = false
    private var lastSavedPage: Int? = null

    /**
     * 책 및 시리즈 데이터를 로드하고 시작 페이지를 설정합니다.
     * 이어 읽기: 서버의 이전 진행도 페이지에서 시작합니다.
     */
    fun loadBook(bookId: String) {
        if (currentBookId == bookId && _uiState.value is ReaderUiState.Reading) {
            return
        }

        currentBookId = bookId
        hasProgressChanged = false
        saveProgressJob?.cancel()

        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading

            try {
                val book = bookRepository.getBookById(bookId)
                currentBook = book

                val series = seriesRepository.getSeriesById(book.seriesId)
                val direction = series.metadata.readingDirection ?: ReadingDirection.LEFT_TO_RIGHT
                currentReadingDirection = direction

                // 이전 읽기 진행이 있으면 해당 페이지에서 시작
                val totalPages = book.pageCount.coerceAtLeast(1)
                val startPage = book.readProgress?.page?.coerceIn(1, totalPages) ?: 1
                lastSavedPage = book.readProgress?.page

                updateReadingState(
                    bookTitle = book.metadata.title.ifEmpty { book.name },
                    currentPage = startPage,
                    totalPages = totalPages,
                    readingDirection = direction,
                )
            } catch (e: Exception) {
                Timber.e(e, "책 로드 실패: $bookId")
                _uiState.value = ReaderUiState.Error(
                    message = e.localizedMessage ?: "책을 불러올 수 없습니다"
                )
            }
        }
    }

    /**
     * D-pad 왼쪽 키 입력 처리
     * RIGHT_TO_LEFT면 다음 페이지, 그 외는 이전 페이지로 이동합니다.
     */
    fun onDpadLeft() {
        if (currentReadingDirection == ReadingDirection.RIGHT_TO_LEFT) {
            nextPage()
        } else {
            previousPage()
        }
    }

    /**
     * D-pad 오른쪽 키 입력 처리
     * RIGHT_TO_LEFT면 이전 페이지, 그 외는 다음 페이지로 이동합니다.
     */
    fun onDpadRight() {
        if (currentReadingDirection == ReadingDirection.RIGHT_TO_LEFT) {
            previousPage()
        } else {
            nextPage()
        }
    }

    /** 다음 페이지로 이동 */
    fun nextPage() {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        if (state.currentPage >= state.totalPages) return

        applyPageChange(state.currentPage + 1)
    }

    /** 이전 페이지로 이동 */
    fun previousPage() {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        if (state.currentPage <= 1) return

        applyPageChange(state.currentPage - 1)
    }

    /**
     * 페이지 점프 (오버레이 조작 등)
     *
     * @param delta 이동할 페이지 수 (+면 다음, -면 이전)
     */
    fun jumpPages(delta: Int) {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        val newPage = (state.currentPage + delta).coerceIn(1, state.totalPages)
        if (newPage != state.currentPage) {
            applyPageChange(newPage)
        }
    }

    /** 10페이지 앞으로 점프 */
    fun jumpForward(count: Int = 10) = jumpPages(count)

    /** 10페이지 뒤로 점프 */
    fun jumpBackward(count: Int = 10) = jumpPages(-count)

    /** 정보 오버레이 토글 */
    fun toggleOverlay() {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        _uiState.value = state.copy(showOverlay = !state.showOverlay)
    }

    /**
     * 리더 종료 시 즉시 진행 상태를 저장합니다.
     * 대기 중인 2초 디바운스를 취소하고 동기화합니다.
     */
    fun saveProgressOnExit(): Job {
        saveProgressJob?.cancel()
        val state = _uiState.value as? ReaderUiState.Reading ?: return Job().apply { complete() }
        val bookId = currentBookId.ifEmpty { return Job().apply { complete() } }

        if (!hasProgressChanged || lastSavedPage == state.currentPage) {
            return Job().apply { complete() }
        }

        return viewModelScope.launch(NonCancellable) {
            saveProgressInternal(bookId, state.currentPage, state.totalPages)
        }
    }

    private fun applyPageChange(newPage: Int) {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        hasProgressChanged = true

        updateReadingState(
            bookTitle = state.bookTitle,
            currentPage = newPage,
            totalPages = state.totalPages,
            readingDirection = state.readingDirection,
        )

        scheduleProgressSave(newPage, state.totalPages)
    }

    /**
     * 2초 디바운스로 읽기 진행 상태 저장을 예약합니다.
     */
    private fun scheduleProgressSave(page: Int, totalPages: Int) {
        saveProgressJob?.cancel()
        val bookId = currentBookId.ifEmpty { return }

        saveProgressJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            saveProgressInternal(bookId, page, totalPages)
        }
    }

    /**
     * 서버에 읽기 진행 상태를 저장합니다.
     * 마지막 페이지에 도달한 경우 completed=true로 설정합니다.
     */
    private suspend fun saveProgressInternal(bookId: String, page: Int, totalPages: Int) {
        val completed = page >= totalPages
        try {
            bookRepository.updateReadProgress(
                bookId = bookId,
                page = page,
                completed = completed,
            )
            lastSavedPage = page
            Timber.d("읽기 진행 저장 완료: $bookId, 페이지 $page/$totalPages, completed=$completed")
        } catch (e: Exception) {
            Timber.e(e, "읽기 진행 저장 실패: $bookId, 페이지 $page")
        }
    }

    /**
     * 읽기 UI 상태를 갱신하고 현재 페이지 및 프리로드(±2) URL을 계산합니다.
     */
    private fun updateReadingState(
        bookTitle: String,
        currentPage: Int,
        totalPages: Int,
        readingDirection: ReadingDirection,
    ) {
        val currentPageUrl = buildPageUrl(currentBookId, currentPage)
        val preloadUrls = buildPreloadUrls(currentBookId, currentPage, totalPages)
        val existingOverlay = (_uiState.value as? ReaderUiState.Reading)?.showOverlay ?: false

        _uiState.value = ReaderUiState.Reading(
            bookTitle = bookTitle,
            currentPage = currentPage,
            totalPages = totalPages,
            currentPageUrl = currentPageUrl,
            preloadUrls = preloadUrls,
            readingDirection = readingDirection,
            showOverlay = existingOverlay,
        )
    }

    /**
     * 이웃 페이지(±2) 이미지 URL 목록을 생성합니다.
     */
    private fun buildPreloadUrls(bookId: String, currentPage: Int, totalPages: Int): List<String> {
        val targetPages = listOf(
            currentPage - 2,
            currentPage - 1,
            currentPage + 1,
            currentPage + 2,
        ).filter { it in 1..totalPages }

        return targetPages.map { buildPageUrl(bookId, it) }
    }

    /**
     * 페이지 이미지 절대 URL 생성
     */
    private fun buildPageUrl(bookId: String, pageNumber: Int): String =
        imageUrls.page(bookId, pageNumber).orEmpty()
}
