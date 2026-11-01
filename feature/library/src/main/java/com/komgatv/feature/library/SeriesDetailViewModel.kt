package com.komgatv.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.error.toLoadError
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.ReadProgressEvents
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.model.Book
import com.komgatv.core.model.Series
import com.komgatv.core.model.LoadError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

    data class Error(val error: LoadError) : SeriesDetailUiState
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
    private val progressEvents: ReadProgressEvents,
    val imageUrls: ImageUrls,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SeriesDetailUiState>(SeriesDetailUiState.Loading)
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()


    private var currentSeriesId: String = ""

    companion object {
        const val PAGE_SIZE = 50
    }

    init {
        // 이 시리즈의 책 진행이 저장되면 (리더를 나온 뒤 늦게 끝나도) 진행 표시를 다시 읽는다
        viewModelScope.launch {
            progressEvents.updates.collect { bookId ->
                val current = _uiState.value as? SeriesDetailUiState.Success ?: return@collect
                if (current.books.any { it.id == bookId }) refreshProgress()
            }
        }
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
                _uiState.value = SeriesDetailUiState.Error(e.toLoadError())
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

    /** 책 메뉴의 결과 — 화면이 짧게 알려 준다 */
    private val _actionMessage = MutableStateFlow<BookActionResult?>(null)
    val actionMessage: StateFlow<BookActionResult?> = _actionMessage.asStateFlow()

    fun markRead(bookId: String) = bookAction(BookActionResult.MarkedRead) { bookRepository.markRead(bookId) }

    fun markUnread(bookId: String) = bookAction(BookActionResult.MarkedUnread) { bookRepository.markUnread(bookId) }

    /** 분석은 서버가 따로 하므로 요청만 보내고, 잠시 뒤 목록을 다시 읽는다 */
    fun analyze(bookId: String) = bookAction(BookActionResult.AnalyzeRequested) { bookRepository.analyze(bookId) }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private fun bookAction(success: BookActionResult, action: suspend () -> Unit) {
        viewModelScope.launch {
            _actionMessage.value = try {
                action()
                // 읽음 표시는 진행 저장 알림으로도 새로고침되지만, 분석은 알림이 없다
                refreshProgress()
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "책 메뉴 실패")
                BookActionResult.Failed
            }
        }
    }

    /**
     * 진행도 새로고침 (리더에서 돌아왔을 때)
     *
     * 이미 불러온 페이지까지 다시 읽어 책 목록만 바꾼다. 로딩 화면으로 바꾸지 않아 포커스·스크롤이 유지된다.
     */
    fun refreshProgress() {
        val current = _uiState.value as? SeriesDetailUiState.Success ?: return
        viewModelScope.launch {
            try {
                val books = (0..current.currentPage).flatMap { page ->
                    bookRepository.getBooks(seriesId = currentSeriesId, page = page, size = PAGE_SIZE).first().books
                }
                val latest = _uiState.value as? SeriesDetailUiState.Success ?: return@launch
                // 그사이 다음 페이지가 덧붙었으면 덮어쓰지 않는다
                if (latest.currentPage != current.currentPage) return@launch
                _uiState.value = latest.copy(books = books)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "진행도 새로고침 실패")
            }
        }
    }
}

/** 책 메뉴 결과 */
enum class BookActionResult { MarkedRead, MarkedUnread, AnalyzeRequested, Failed }
