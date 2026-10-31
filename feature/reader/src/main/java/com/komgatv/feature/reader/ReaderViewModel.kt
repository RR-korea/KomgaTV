package com.komgatv.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import com.komgatv.core.data.error.toLoadError
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.BookRepository
import com.komgatv.core.data.repository.SeriesRepository
import com.komgatv.core.datastore.ReaderPreferences
import com.komgatv.core.model.Book
import com.komgatv.core.model.BookPage
import com.komgatv.core.model.LoadError
import com.komgatv.core.model.MediaProfile
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.ReaderType
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import com.komgatv.feature.reader.continuous.ContinuousProgressHelper
import com.komgatv.feature.reader.continuous.ContinuousTilePlanner
import com.komgatv.feature.reader.continuous.PageTile
import com.komgatv.feature.reader.continuous.ReaderTypeResolver
import com.komgatv.feature.reader.continuous.TileSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
     * @property totalPages 전체 페이지 수
     * @property readerType 리더 종류 (PAGED 또는 CONTINUOUS)
     * @property currentSpread 현재 펼침 (Paged 모드)
     * @property currentSpreadIndex 현재 펼침 인덱스 (0-based, Paged 모드)
     * @property totalSpreads 전체 펼침 수 (Paged 모드)
     * @property spreadPageUrls 현재 펼침의 페이지 이미지 절대 URL 목록 (Paged 모드)
     * @property preloadSpreads 프리로드할 앞뒤 2개 펼침 (Paged 모드)
     * @property readingDirection 현재 적용된 읽기 방향
     * @property pageLayout 현재 적용된 페이지 배치 레이아웃
     * @property layoutOffset 쪽 맞춤 밀기 적용 여부
     * @property scaleType 페이지 크기 맞춤 방식
     * @property continuousTiles 세로 스크롤 타일 목록 (Continuous 모드)
     * @property continuousPadding 세로 스크롤 좌우 여백 (화면 너비 비율)
     * @property continuousPageSpacing 세로 스크롤 쪽 사이 간격 dp
     * @property currentContinuousPage 화면에 보이는 쪽 중 가장 큰 번호 (Continuous 모드, 진행 표시·저장용)
     * @property scrollToTileIndex 화면이 스크롤해야 할 타일 인덱스 (처음 열 때, 쪽 이동, 점프). 스크롤한 뒤 onTileScrolled
     * @property stretchToFit 작은 이미지 확대 여부
     * @property showOverlay 정보 오버레이 표시 여부
     * @property showSettingsPanel 보기 설정 패널 표시 여부
     */
    data class Reading(
        val bookTitle: String,
        val totalPages: Int,
        val readerType: ReaderType,
        val currentSpread: PageSpread,
        val currentSpreadIndex: Int,
        val totalSpreads: Int,
        val spreadPageUrls: List<String>,
        val preloadSpreads: List<List<String>>,
        val readingDirection: ReadingDirection,
        val pageLayout: PageLayout,
        val layoutOffset: Boolean,
        val scaleType: ScaleType,
        val continuousTiles: List<PageTile>,
        val continuousPadding: Float,
        val continuousPageSpacing: Int,
        val currentContinuousPage: Int,
        val scrollToTileIndex: Int? = null,
        val stretchToFit: Boolean,
        val showOverlay: Boolean = false,
        val showSettingsPanel: Boolean = false,
    ) : ReaderUiState

    /** 책을 불러오지 못함 — 화면이 오류 종류를 문구로 바꾼다 */
    data class Error(val error: LoadError) : ReaderUiState

    /** EPUB — 페이지 이미지가 아니라 글 리더로 연다 */
    data object Epub : ReaderUiState
}

/**
 * 리더 화면 ViewModel
 *
 * Komelia(Apache-2.0, Snd-R/Komelia)와 동일한 규칙을 따릅니다:
 * - 리더 종류 우선순위: 시리즈 방향 LTR/RTL -> PAGED, WEBTOON -> CONTINUOUS, VERTICAL/없음 -> 저장된 readerType
 * - 이어 읽기: 진행이 없거나 completed면 1쪽부터, 아니면 저장된 쪽부터 (완독 책은 1쪽부터)
 * - 설정 변경 시 즉시 반영 및 전역 기본값 저장
 * - 페이지 또는 타일 단위 탐색 및 진행 저장 (2초 디바운스 + 종료 시 즉시)
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val seriesRepository: SeriesRepository,
    private val imageUrls: ImageUrls,
    private val readerPreferences: ReaderPreferences,
    private val tileSource: TileSource,
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

    // Paged 모드 상태
    private var currentSpreads: List<PageSpread> = emptyList()
    private var currentSpreadIndex: Int = 0

    // Continuous 모드 상태
    private var currentTiles: List<PageTile> = emptyList()
    private var currentContinuousPage: Int = 1
    private var firstVisibleTile: PageTile? = null
    private var continuousCompleted = false
    private val boundsRequested = mutableSetOf<Int>()
    private var screenWidthPx: Int = 1920
    private var screenHeightPx: Int = 1080
    private var scrollToTileIndex: Int? = null

    // 설정 상태
    private var currentReaderType: ReaderType = ReaderType.PAGED
    private var currentLayout: PageLayout = PageLayout.SINGLE_PAGE
    private var currentScaleType: ScaleType = ScaleType.SCREEN
    private var currentStretchToFit: Boolean = true
    private var currentReadingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT
    private var layoutOffset: Boolean = false
    private var currentContinuousPadding: Float = 0f
    private var currentContinuousPageSpacing: Int = 0

    private var saveProgressJob: Job? = null
    private var hasProgressChanged = false
    private var lastSavedPage: Int? = null

    /**
     * 화면 크기를 갱신하고 타일 목록을 재계획합니다.
     */
    fun setScreenSize(widthPx: Int, heightPx: Int) {
        if (screenWidthPx == widthPx && screenHeightPx == heightPx) return
        screenWidthPx = widthPx.coerceAtLeast(1)
        screenHeightPx = heightPx.coerceAtLeast(1)
        if (currentBookPages.isNotEmpty()) {
            rebuildContinuousTiles()
            updateReadingState()
        }
    }

    /**
     * 책 및 시리즈 데이터를 로드하고 시작 위치를 설정합니다.
     * 이어 읽기: 완독(completed)이거나 진행이 없으면 1쪽, 아니면 저장된 쪽부터 시작합니다.
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
                if (book.mediaProfile == MediaProfile.EPUB) {
                    _uiState.value = ReaderUiState.Epub
                    return@launch
                }
                currentBook = book

                val series = seriesRepository.getSeriesById(book.seriesId)

                // 1. 리더 종류 우선순위
                currentReaderType = ReaderTypeResolver.resolveReaderType(
                    seriesReadingDirection = series.metadata.readingDirection,
                    savedReaderType = savedSettings.readerType,
                )

                // 2. Paged 읽기 방향
                currentReadingDirection = series.metadata.readingDirection
                    ?.takeIf { it == ReadingDirection.LEFT_TO_RIGHT || it == ReadingDirection.RIGHT_TO_LEFT }
                    ?: savedSettings.readingDirection

                // 3. 설정값 적용
                currentLayout = savedSettings.pageLayout
                currentScaleType = savedSettings.scaleType
                currentStretchToFit = savedSettings.stretchToFit
                currentContinuousPadding = savedSettings.continuousPadding
                currentContinuousPageSpacing = savedSettings.continuousPageSpacing
                layoutOffset = false // 세션 동안만 적용

                // 4. 페이지 크기 목록 로드
                val fetchedPages = try {
                    bookRepository.getBookPages(bookId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 쪽 수도 모르면 읽을 수 없다 (서버에서 책 분석 실패 등) — 오류로 보여 준다
                    if (book.pageCount <= 0) throw e
                    Timber.w(e, "페이지 목록 조회 실패, 크기 없이 진행: $bookId")
                    emptyList()
                }

                currentBookPages = if (fetchedPages.isNotEmpty()) {
                    fetchedPages
                } else {
                    val totalPages = book.pageCount.coerceAtLeast(1)
                    (1..totalPages).map { BookPage(number = it) }
                }

                // 5. Paged 펼침 및 Continuous 타일 계획 구성
                currentSpreads = SpreadMapBuilder.buildSpreadMap(
                    pages = currentBookPages,
                    layout = currentLayout,
                    offset = layoutOffset,
                )
                rebuildContinuousTiles()

                // 6. 이어 읽기: 완독(completed)이거나 진행이 없으면 1쪽, 아니면 저장된 쪽부터 시작
                val isCompleted = book.readProgress?.completed == true
                val progressPage = if (!isCompleted) book.readProgress?.page else null
                val startPageNumber = progressPage?.coerceIn(1, currentBookPages.size.coerceAtLeast(1)) ?: 1

                currentSpreadIndex = currentSpreads.indexOfFirst { it.containsPage(startPageNumber) }.coerceAtLeast(0)
                currentContinuousPage = startPageNumber
                firstVisibleTile = null
                continuousCompleted = false
                boundsRequested.clear()
                scrollToTileIndex = if (startPageNumber > 1) firstTileIndexOf(startPageNumber) else null
                lastSavedPage = progressPage

                updateReadingState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "책 로드 실패: $bookId")
                _uiState.value = ReaderUiState.Error(e.toLoadError())
            }
        }
    }

    /**
     * D-pad 왼쪽 키 입력 처리
     */
    fun onDpadLeft() {
        if (currentReaderType == ReaderType.PAGED) {
            if (currentReadingDirection == ReadingDirection.RIGHT_TO_LEFT) {
                nextSpread()
            } else {
                previousSpread()
            }
        } else {
            // Continuous 모드: 이전 쪽의 첫 타일로 이동
            previousContinuousPage()
        }
    }

    /**
     * D-pad 오른쪽 키 입력 처리
     */
    fun onDpadRight() {
        if (currentReaderType == ReaderType.PAGED) {
            if (currentReadingDirection == ReadingDirection.RIGHT_TO_LEFT) {
                previousSpread()
            } else {
                nextSpread()
            }
        } else {
            // Continuous 모드: 다음 쪽의 첫 타일로 이동
            nextContinuousPage()
        }
    }

    /** Paged: 다음 펼침으로 이동 */
    fun nextSpread() {
        if (currentSpreadIndex >= currentSpreads.lastIndex) return
        applySpreadChange(currentSpreadIndex + 1)
    }

    /** Paged: 이전 펼침으로 이동 */
    fun previousSpread() {
        if (currentSpreadIndex <= 0) return
        applySpreadChange(currentSpreadIndex - 1)
    }

    /** Continuous: 맨 위 타일이 쪽 중간이면 그 쪽 처음, 쪽 처음이면 이전 쪽 처음으로 */
    private fun previousContinuousPage() {
        val top = firstVisibleTile ?: return
        val target = if (top.tileIndex > 0) top.pageNumber else top.pageNumber - 1
        if (target >= 1) scrollToPage(target)
    }

    /** Continuous: 맨 위에 보이는 쪽의 다음 쪽 처음으로 */
    private fun nextContinuousPage() {
        val top = firstVisibleTile?.pageNumber ?: currentContinuousPage
        if (top < currentBookPages.size) scrollToPage(top + 1)
    }

    private fun scrollToPage(pageNumber: Int) {
        scrollToTileIndex = firstTileIndexOf(pageNumber)
        updateReadingState()
    }

    private fun firstTileIndexOf(pageNumber: Int): Int =
        currentTiles.indexOfFirst { it.pageNumber == pageNumber }.coerceAtLeast(0)

    /** 스크롤 요청 소비 */
    fun onTileScrolled() {
        if (scrollToTileIndex != null) {
            scrollToTileIndex = null
            val state = _uiState.value as? ReaderUiState.Reading ?: return
            _uiState.value = state.copy(scrollToTileIndex = null)
        }
    }

    /**
     * 페이지 단위 점프 (오버레이 등)
     *
     * @param delta 이동할 페이지 수 (+면 다음, -면 이전)
     */
    fun jumpPages(delta: Int) {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        if (currentReaderType == ReaderType.PAGED) {
            val currentPageNumber = state.currentSpread.pages.firstOrNull()?.number ?: 1
            val targetPageNumber = (currentPageNumber + delta).coerceIn(1, state.totalPages)
            val targetSpreadIndex = currentSpreads.indexOfFirst { it.containsPage(targetPageNumber) }
            if (targetSpreadIndex >= 0 && targetSpreadIndex != currentSpreadIndex) {
                applySpreadChange(targetSpreadIndex)
            }
        } else {
            val from = firstVisibleTile?.pageNumber ?: currentContinuousPage
            scrollToPage((from + delta).coerceIn(1, state.totalPages))
        }
    }

    /** 정보 오버레이 토글 */
    fun toggleOverlay() {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        val newOverlay = !state.showOverlay
        _uiState.value = state.copy(
            showOverlay = newOverlay,
            showSettingsPanel = if (!newOverlay) false else state.showSettingsPanel,
        )
    }

    /** 정보 오버레이 닫기 */
    fun closeOverlay() {
        val state = _uiState.value as? ReaderUiState.Reading ?: return
        _uiState.value = state.copy(showOverlay = false, showSettingsPanel = false)
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

    /** 리더 종류(ReaderType) 변경 */
    fun setReaderType(type: ReaderType) {
        if (currentReaderType == type) return
        currentReaderType = type
        // 보던 쪽을 다른 모드에서도 이어서 본다
        if (type == ReaderType.CONTINUOUS) {
            val page = currentSpreads.getOrNull(currentSpreadIndex)?.pages?.firstOrNull()?.number ?: 1
            rebuildContinuousTiles()
            firstVisibleTile = null
            scrollToTileIndex = firstTileIndexOf(page)
        } else {
            val page = firstVisibleTile?.pageNumber ?: currentContinuousPage
            currentSpreadIndex = currentSpreads.indexOfFirst { it.containsPage(page) }.coerceAtLeast(0)
        }
        updateReadingState()
        viewModelScope.launch {
            readerPreferences.saveReaderType(type)
        }
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

    /** 쪽 맞춤 밀기(offset) 토글 */
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
        if (currentReaderType == ReaderType.CONTINUOUS) {
            rebuildContinuousTiles()
        }
        updateReadingState()
        viewModelScope.launch {
            readerPreferences.saveStretchToFit(stretch)
        }
    }

    /** 읽기 방향(ReadingDirection) 변경 */
    fun setReadingDirection(direction: ReadingDirection) {
        currentReadingDirection = direction
        updateReadingState()
        viewModelScope.launch {
            readerPreferences.saveReadingDirection(direction)
        }
    }

    /** 세로 스크롤 좌우 여백(continuousPadding) 변경 */
    fun setContinuousPadding(padding: Float) {
        if (currentContinuousPadding == padding) return
        currentContinuousPadding = padding
        rebuildContinuousTiles()
        updateReadingState()
        viewModelScope.launch {
            readerPreferences.saveContinuousPadding(padding)
        }
    }

    /** 세로 스크롤 쪽 사이 간격(continuousPageSpacing) 변경 */
    fun setContinuousPageSpacing(spacing: Int) {
        if (currentContinuousPageSpacing == spacing) return
        currentContinuousPageSpacing = spacing
        rebuildContinuousTiles()
        updateReadingState()
        viewModelScope.launch {
            readerPreferences.saveContinuousPageSpacing(spacing)
        }
    }

    /** Continuous: 타일 이미지 (화면이 보이는 타일마다 요청) */
    suspend fun loadTile(tile: PageTile): ImageBitmap = tileSource.tile(currentBookId, tile)

    /**
     * Continuous: 화면에 보이는 타일이 바뀜 (PageTile.key 목록, 위에서부터).
     * 보이는 쪽 중 가장 큰 번호를 2초 뒤 저장하고, 마지막 쪽의 마지막 타일이 보이면 완독.
     */
    fun onContinuousVisibleTilesChanged(visibleKeys: List<String>) {
        if (currentReaderType != ReaderType.CONTINUOUS) return
        val visible = visibleKeys.mapNotNull { key -> currentTiles.firstOrNull { it.key == key } }
        if (visible.isEmpty()) return
        firstVisibleTile = visible.first()
        visible.filter { it.isPlaceholder }.forEach { requestBounds(it.pageNumber) }
        val totalPages = currentBookPages.size.coerceAtLeast(1)
        val progress = ContinuousProgressHelper.calculateProgress(visible, totalPages) ?: return

        if (currentContinuousPage == progress.pageNumber && continuousCompleted == progress.completed) return
        currentContinuousPage = progress.pageNumber
        continuousCompleted = progress.completed
        hasProgressChanged = true
        (_uiState.value as? ReaderUiState.Reading)?.let {
            _uiState.value = it.copy(currentContinuousPage = progress.pageNumber)
        }
        preloadNextContinuousPages(currentBookId, progress.pageNumber)
        scheduleProgressSave(progress.pageNumber, totalPages, progress.completed)
    }

    /** 크기를 모르는 쪽이 보이면 파일을 받아 크기를 알아내고 그 쪽 타일을 다시 계획한다 */
    private fun requestBounds(pageNumber: Int) {
        if (!boundsRequested.add(pageNumber)) return
        val bookId = currentBookId
        viewModelScope.launch {
            val size = try {
                tileSource.bounds(bookId, pageNumber)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "쪽 크기 확인 실패: $bookId, $pageNumber")
                null
            } ?: return@launch
            if (bookId != currentBookId) return@launch
            currentBookPages = currentBookPages.map {
                if (it.number == pageNumber) it.copy(width = size.first, height = size.second) else it
            }
            rebuildContinuousTiles()
            updateReadingState()
        }
    }

    override fun onCleared() {
        tileSource.clear()
    }

    /**
     * 리더 종료 시 즉시 진행 상태를 저장합니다.
     */
    fun saveProgressOnExit(): Job {
        saveProgressJob?.cancel()
        val state = _uiState.value as? ReaderUiState.Reading ?: return Job().apply { complete() }
        val bookId = currentBookId.ifEmpty { return Job().apply { complete() } }

        val pageToSave: Int
        val completed: Boolean

        if (currentReaderType == ReaderType.CONTINUOUS) {
            pageToSave = currentContinuousPage
            completed = continuousCompleted
        } else {
            pageToSave = state.currentSpread.highestPageNumber
            completed = state.currentSpread.containsPage(state.totalPages) || pageToSave >= state.totalPages
        }

        if (!hasProgressChanged || lastSavedPage == pageToSave) {
            return Job().apply { complete() }
        }

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

    private fun scheduleProgressSave(page: Int, totalPages: Int, completed: Boolean) {
        saveProgressJob?.cancel()
        val bookId = currentBookId.ifEmpty { return }

        saveProgressJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            saveProgressInternal(bookId, page, totalPages, completed)
        }
    }

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
     * 타일을 다시 계획한다. 세로 스크롤 중이면 타일 번호가 바뀌어도 보던 자리(같은 쪽의 같은 높이)로 다시 스크롤한다.
     */
    private fun rebuildContinuousTiles() {
        val anchor = firstVisibleTile
        currentTiles = ContinuousTilePlanner.planPages(
            currentBookPages,
            ContinuousTilePlanner.PlanParams(
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                sidePaddingFraction = currentContinuousPadding,
                pageSpacingDp = currentContinuousPageSpacing,
                stretchToFit = currentStretchToFit,
            ),
        )
        if (currentReaderType == ReaderType.CONTINUOUS && anchor != null) {
            scrollToTileIndex = tileIndexAt(anchor)
        }
    }

    /** 새 타일 목록에서 [anchor]의 윗부분이 들어 있는 타일 */
    private fun tileIndexAt(anchor: PageTile): Int {
        val top = anchor.region?.top ?: 0
        val index = currentTiles.indexOfFirst {
            it.pageNumber == anchor.pageNumber && (it.region?.bottom ?: Int.MAX_VALUE) > top
        }
        return if (index >= 0) index else firstTileIndexOf(anchor.pageNumber)
    }

    private fun updateReadingState() {
        val book = currentBook ?: return
        val currentSpread = currentSpreads.getOrNull(currentSpreadIndex)
            ?: PageSpread(emptyList())

        val totalPages = currentBookPages.size.coerceAtLeast(1)
        val spreadUrls = currentSpread.pages.mapNotNull { imageUrls.page(currentBookId, it.number) }
        val preloadSpreads = buildPreloadSpreads(currentBookId, currentSpreadIndex, currentSpreads)

        val existingOverlay = (_uiState.value as? ReaderUiState.Reading)?.showOverlay ?: false
        val existingSettings = (_uiState.value as? ReaderUiState.Reading)?.showSettingsPanel ?: false

        _uiState.value = ReaderUiState.Reading(
            bookTitle = book.metadata.title.ifEmpty { book.name },
            totalPages = totalPages,
            readerType = currentReaderType,
            currentSpread = currentSpread,
            currentSpreadIndex = currentSpreadIndex,
            totalSpreads = currentSpreads.size,
            spreadPageUrls = spreadUrls,
            preloadSpreads = preloadSpreads,
            readingDirection = currentReadingDirection,
            pageLayout = currentLayout,
            layoutOffset = layoutOffset,
            scaleType = currentScaleType,
            continuousTiles = currentTiles,
            continuousPadding = currentContinuousPadding,
            continuousPageSpacing = currentContinuousPageSpacing,
            currentContinuousPage = currentContinuousPage,
            scrollToTileIndex = scrollToTileIndex,
            stretchToFit = currentStretchToFit,
            showOverlay = existingOverlay,
            showSettingsPanel = existingSettings,
        )
    }

    private fun buildPreloadSpreads(
        bookId: String,
        spreadIndex: Int,
        spreads: List<PageSpread>,
    ): List<List<String>> {
        val targetIndices = listOf(
            spreadIndex - 2,
            spreadIndex - 1,
            spreadIndex + 1,
            spreadIndex + 2,
        ).filter { it in spreads.indices }

        return targetIndices.map { idx ->
            spreads[idx].pages.mapNotNull { page ->
                imageUrls.page(bookId, page.number)
            }
        }
    }

    private fun preloadNextContinuousPages(bookId: String, currentVisibleMaxPage: Int) {
        val targetPages = listOf(currentVisibleMaxPage + 1, currentVisibleMaxPage + 2)
            .filter { it in 1..currentBookPages.size }
        viewModelScope.launch {
            for (page in targetPages) {
                try {
                    tileSource.prefetch(bookId, page)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "다음 쪽 미리 받기 실패: $bookId, $page")
                }
            }
        }
    }
}
