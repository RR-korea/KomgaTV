package com.komgatv.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.datastore.ReaderPreferences
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookPage
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
     * @property currentSpread 현재 펼침
     * @property currentSpreadIndex 현재 펼침 인덱스 (0-based)
     * @property totalSpreads 전체 펼침 수
     * @property totalPages 전체 페이지 수
     * @property spreadPageUrls 현재 펼침의 페이지 이미지 절대 URL 목록 (1개 또는 2개)
     * @property preloadUrls 프리로드 대상 펼침(앞뒤 2개)의 이미지 URL 목록
     * @property readingDirection 현재 적용된 읽기 방향
     * @property pageLayout 현재 적용된 페이지 배치 레이아웃
     * @property layoutOffset 쪽 맞춤 밀기 적용 여부
     * @property scaleType 페이지 크기 맞춤 방식
     * @property stretchToFit 작은 이미지 확대 여부
     * @property showOverlay 정보 오버레이 표시 여부
     * @property showSettingsPanel 보기 설정 패널 표시 여부
     */
    data class Reading(
        val bookTitle: String,
        val currentSpread: PageSpread,
        val currentSpreadIndex: Int,
        val totalSpreads: Int,
        val totalPages: Int,
        val spreadPageUrls: List<String>,
        val preloadUrls: List<String>,
        val readingDirection: ReadingDirection,
        val pageLayout: PageLayout,
        val layoutOffset: Boolean,
        val scaleType: ScaleType,
        val stretchToFit: Boolean,
        val showOverlay: Boolean = false,
        val showSettingsPanel: Boolean = false,
    ) : ReaderUiState {
        /** 하위 호환 및 단일 페이지 참조용 프로퍼티 */
        val currentPage: Int get() = currentSpread.pages.firstOrNull()?.number ?: 1
        val currentPageUrl: String get() = spreadPageUrls.firstOrNull().orEmpty()
    }

    /** 에러 상태 */
    data class Error(val message: String) : ReaderUiState
}

/**
 * 리더 화면 ViewModel
 *
 * Komelia 페이지 리더 옵션(쪽 배치, 밀기, 읽기 방향 우선순위, 크기 맞춤, 작은 이미지 키우기)을 지원합니다.
 * - 읽기 방향 우선순위: 시리즈 메타데이터 > ReaderPreferences 전역 기본값
 * - 리더에서 방향 변경 시 현재 책 즉시 반영 및 전역 기본값 저장
 * - 두 쪽 보기: 펼침(Spread) 단위 이동 및 프리로드(앞뒤 2개 펼침)
 * - 진행 저장: 펼침 내 가장 큰 페이지 번호 저장, 마지막 페이지 포함 시 completed=true
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val seriesRepository: SeriesRepository,
    private val imageUrls: ImageUrls,
    private val readerPreferences: ReaderPreferences,
) : ViewModel() {

    companion object {
        /** 진행 저장 디바운스 시간 (2초) */
        const val SAVE_DEBOUNCE_MILLIS = 2000L
    }

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBook: Book? = null
    private var currentBookId: String = ""
    private var currentBookPages: List<BookPage> = emptyList()
    private var currentSpreads: List<PageSpread> = emptyList()
    private var currentSpreadIndex: Int = 0

    private var currentLayout: PageLayout = PageLayout.SINGLE_PAGE
    private var currentScaleType: ScaleType = ScaleType.SCREEN
    private var currentStretchToFit: Boolean = true
    private var currentReadingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT
    private var layoutOffset: Boolean = false

    private var saveProgressJob: Job? = null
    private var hasProgressChanged = false
    private var lastSavedPage: Int? = null

    /**
     * 책 및 시리즈 데이터를 로드하고 시작 펼침을 설정합니다.
     * 이어 읽기: 서버의 이전 진행도 페이지가 포함된 펼침에서 시작합니다.
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
                val savedSettings = readerPreferences.readerSettings.first()
                val book = bookRepository.getBookById(bookId)
                currentBook = book

                val series = seriesRepository.getSeriesById(book.seriesId)
                // 읽기 방향 우선순위: 시리즈 메타데이터 > 저장된 기본값
                val direction = series.metadata.readingDirection ?: savedSettings.readingDirection
                currentReadingDirection = direction

                currentLayout = savedSettings.pageLayout
                currentScaleType = savedSettings.scaleType
                currentStretchToFit = savedSettings.stretchToFit
                layoutOffset = false // 세션 동안만 적용되므로 책 로드 시 false 초기화

                // 페이지 메타데이터(크기 등) 조회, 실패 시 fallback
                val fetchedPages = runCatching { bookRepository.getBookPages(bookId) }
                    .getOrNull().orEmpty()

                currentBookPages = if (fetchedPages.isNotEmpty()) {
                    fetchedPages
                } else {
                    val totalPages = book.pageCount.coerceAtLeast(1)
                    (1..totalPages).map { BookPage(number = it) }
                }

                currentSpreads = SpreadMapBuilder.buildSpreadMap(
                    pages = currentBookPages,
                    layout = currentLayout,
                    offset = layoutOffset,
                )

                // 이전 읽기 진행이 있으면 해당 페이지가 포함된 펼침에서 시작
                val progressPage = book.readProgress?.page
                val startSpreadIndex = if (progressPage != null) {
                    currentSpreads.indexOfFirst { it.containsPage(progressPage) }.takeIf { it >= 0 } ?: 0
                } else {
                    0
                }

                currentSpreadIndex = startSpreadIndex.coerceIn(0, (currentSpreads.size - 1).coerceAtLeast(0))
                lastSavedPage = book.readProgress?.page

                updateReadingState()
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
     * RIGHT_TO_LEFT면 다음 펼침, 그 외는 이전 펼침으로 이동합니다.
     */
    fun onDpadLeft() {
        if (currentReadingDirection == ReadingDirection.RIGHT_TO_LEFT) {
            nextSpread()
        } else {
            previousSpread()
        }
    }

    /**
     * D-pad 오른쪽 키 입력 처리
     * RIGHT_TO_LEFT면 이전 펼침, 그 외는 다음 펼침으로 이동합니다.
     */
    fun onDpadRight() {
        if (currentReadingDirection == ReadingDirection.RIGHT_TO_LEFT) {
            previousSpread()
        } else {
            nextSpread()
        }
    }

    /** 다음 펼침으로 이동 */
    fun nextSpread() {
        if (currentSpreadIndex >= currentSpreads.lastIndex) return
        applySpreadChange(currentSpreadIndex + 1)
    }

    /** 이전 펼침으로 이동 */
    fun previousSpread() {
        if (currentSpreadIndex <= 0) return
        applySpreadChange(currentSpreadIndex - 1)
    }

    /** 하위 호환용 다음 페이지 */
    fun nextPage() = nextSpread()

    /** 하위 호환용 이전 페이지 */
    fun previousPage() = previousSpread()

    /**
     * 페이지 단위 점프 (오버레이 조작 등)
     *
     * @param delta 이동할 페이지 수 (+면 다음, -면 이전)
     */
    fun jumpPages(delta: Int) {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        val currentPageNumber = state.currentSpread.pages.firstOrNull()?.number ?: 1
        val targetPageNumber = (currentPageNumber + delta).coerceIn(1, state.totalPages)

        val targetSpreadIndex = currentSpreads.indexOfFirst { it.containsPage(targetPageNumber) }
        if (targetSpreadIndex >= 0 && targetSpreadIndex != currentSpreadIndex) {
            applySpreadChange(targetSpreadIndex)
        }
    }

    /** 10페이지 앞으로 점프 */
    fun jumpForward(count: Int = 10) = jumpPages(count)

    /** 10페이지 뒤로 점프 */
    fun jumpBackward(count: Int = 10) = jumpPages(-count)

    /** 정보 오버레이 토글 */
    fun toggleOverlay() {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        val newOverlay = !state.showOverlay
        _uiState.value = state.copy(
            showOverlay = newOverlay,
            showSettingsPanel = if (!newOverlay) false else state.showSettingsPanel,
        )
    }

    /** 보기 설정 패널 열기 */
    fun openSettingsPanel() {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        _uiState.value = state.copy(showOverlay = true, showSettingsPanel = true)
    }

    /** 보기 설정 패널 닫기 */
    fun closeSettingsPanel() {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        _uiState.value = state.copy(showSettingsPanel = false)
    }

    /** 쪽 배치(PageLayout) 변경 */
    fun setPageLayout(layout: PageLayout) {
        if (currentLayout == layout) return
        currentLayout = layout
        rebuildSpreadsAndRetainPage()
        viewModelScope.launch {
            readerPreferences.savePageLayout(layout)
        }
    }

    /** 쪽 맞춤 밀기(offset) 토글 (세션 동안만 적용) */
    fun setLayoutOffset(offset: Boolean) {
        if (layoutOffset == offset) return
        layoutOffset = offset
        rebuildSpreadsAndRetainPage()
    }

    /** 크기 맞춤(ScaleType) 변경 */
    fun setScaleType(scaleType: ScaleType) {
        if (currentScaleType == scaleType) return
        currentScaleType = scaleType
        updateReadingState()
        viewModelScope.launch {
            readerPreferences.saveScaleType(scaleType)
        }
    }

    /** 작은 이미지 키우기(stretchToFit) 변경 */
    fun setStretchToFit(stretch: Boolean) {
        if (currentStretchToFit == stretch) return
        currentStretchToFit = stretch
        updateReadingState()
        viewModelScope.launch {
            readerPreferences.saveStretchToFit(stretch)
        }
    }

    /**
     * 읽기 방향(ReadingDirection) 변경
     *
     * 지금 책에 바로 적용하고 기본값으로 저장합니다 (Komelia와 동일).
     */
    fun setReadingDirection(direction: ReadingDirection) {
        currentReadingDirection = direction
        updateReadingState()
        viewModelScope.launch {
            readerPreferences.saveReadingDirection(direction)
        }
    }

    /**
     * 리더 종료 시 즉시 진행 상태를 저장합니다.
     * 대기 중인 2초 디바운스를 취소하고 동기화합니다.
     */
    fun saveProgressOnExit(): Job {
        saveProgressJob?.cancel()
        val state = _uiState.value as? ReaderUiState.Reading ?: return Job().apply { complete() }
        val bookId = currentBookId.ifEmpty { return Job().apply { complete() } }

        val pageToSave = state.currentSpread.highestPageNumber
        if (!hasProgressChanged || lastSavedPage == pageToSave) {
            return Job().apply { complete() }
        }

        val completed = state.currentSpread.containsPage(state.totalPages) || pageToSave >= state.totalPages
        return viewModelScope.launch(NonCancellable) {
            saveProgressInternal(bookId, pageToSave, state.totalPages, completed)
        }
    }

    private fun applySpreadChange(newSpreadIndex: Int) {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        currentSpreadIndex = newSpreadIndex
        hasProgressChanged = true

        updateReadingState()

        val spread = currentSpreads[newSpreadIndex]
        val pageToSave = spread.highestPageNumber
        val isCompleted = spread.containsPage(state.totalPages) || pageToSave >= state.totalPages
        scheduleProgressSave(pageToSave, state.totalPages, isCompleted)
    }

    /**
     * 2초 디바운스로 읽기 진행 상태 저장을 예약합니다.
     */
    private fun scheduleProgressSave(page: Int, totalPages: Int, completed: Boolean) {
        saveProgressJob?.cancel()
        val bookId = currentBookId.ifEmpty { return }

        saveProgressJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            saveProgressInternal(bookId, page, totalPages, completed)
        }
    }

    /**
     * 서버에 읽기 진행 상태를 저장합니다.
     * 펼침에서 보이는 쪽 중 큰 번호를 저장하고, 마지막 쪽이 보이면 completed=true로 설정합니다.
     */
    private suspend fun saveProgressInternal(bookId: String, page: Int, totalPages: Int, completed: Boolean) {
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
     * 레이아웃이나 오프셋 변경 시 현재 보고 있던 페이지를 유지하면서 펼침 맵을 재계산합니다.
     */
    private fun rebuildSpreadsAndRetainPage() {
        val currentPageNumber = currentSpreads.getOrNull(currentSpreadIndex)
            ?.pages?.firstOrNull()?.number ?: 1

        currentSpreads = SpreadMapBuilder.buildSpreadMap(
            pages = currentBookPages,
            layout = currentLayout,
            offset = layoutOffset,
        )

        val newSpreadIndex = currentSpreads.indexOfFirst { it.containsPage(currentPageNumber) }
        currentSpreadIndex = if (newSpreadIndex >= 0) newSpreadIndex else 0

        updateReadingState()
    }

    /**
     * 읽기 UI 상태를 갱신하고 현재 펼침 및 프리로드(앞뒤 2개 펼침) URL을 계산합니다.
     */
    private fun updateReadingState() {
        val book = currentBook ?: return
        val currentSpread = currentSpreads.getOrNull(currentSpreadIndex)
            ?: PageSpread(emptyList())

        val totalPages = currentBookPages.size.coerceAtLeast(book.pageCount).coerceAtLeast(1)
        val spreadUrls = currentSpread.pages.mapNotNull { imageUrls.page(currentBookId, it.number) }
        val preloadUrls = buildPreloadUrls(currentBookId, currentSpreadIndex, currentSpreads)

        val existingOverlay = (_uiState.value as? ReaderUiState.Reading)?.showOverlay ?: false
        val existingSettings = (_uiState.value as? ReaderUiState.Reading)?.showSettingsPanel ?: false

        _uiState.value = ReaderUiState.Reading(
            bookTitle = book.metadata.title.ifEmpty { book.name },
            currentSpread = currentSpread,
            currentSpreadIndex = currentSpreadIndex,
            totalSpreads = currentSpreads.size,
            totalPages = totalPages,
            spreadPageUrls = spreadUrls,
            preloadUrls = preloadUrls,
            readingDirection = currentReadingDirection,
            pageLayout = currentLayout,
            layoutOffset = layoutOffset,
            scaleType = currentScaleType,
            stretchToFit = currentStretchToFit,
            showOverlay = existingOverlay,
            showSettingsPanel = existingSettings,
        )
    }

    /**
     * 앞뒤 2개 펼침의 모든 페이지 절대 URL 목록을 생성합니다.
     */
    private fun buildPreloadUrls(
        bookId: String,
        spreadIndex: Int,
        spreads: List<PageSpread>,
    ): List<String> {
        val targetIndices = listOf(
            spreadIndex - 2,
            spreadIndex - 1,
            spreadIndex + 1,
            spreadIndex + 2,
        ).filter { it in spreads.indices }

        return targetIndices.flatMap { idx ->
            spreads[idx].pages.mapNotNull { page ->
                imageUrls.page(bookId, page.number)
            }
        }
    }
}
