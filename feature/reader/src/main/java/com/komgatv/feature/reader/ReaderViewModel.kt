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
import com.komgatv.core.model.displayName
import com.komgatv.core.model.ImageEnhancement
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.PageTransition
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.model.ReaderType
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import com.komgatv.feature.reader.continuous.ContinuousProgressHelper
import com.komgatv.feature.reader.continuous.ContinuousTilePlanner
import com.komgatv.feature.reader.continuous.PageTile
import com.komgatv.feature.reader.continuous.ReaderTypeResolver
import com.komgatv.feature.reader.continuous.TileSource
import com.komgatv.feature.reader.enhance.ImageEnhancer
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
        val imageEnhancement: ImageEnhancement = ImageEnhancement.OFF,
        /** 크기 맞춤 결과를 더 확대하는 비율 % (페이지 넘김) */
        val zoomPercent: Int = ReaderSettings.MIN_ZOOM_PERCENT,
        /** 대비 % (100 = 그대로) */
        val contrastPercent: Int = 100,
        /** 페이지 넘김 화면 전환 효과 */
        val pageTransition: PageTransition = PageTransition.SLIDE,
        /**
         * 지금 펼침으로 어떻게 왔는지 — +1 다음 펼침, −1 이전 펼침, 0 그 밖(처음 열기·점프·설정 변경).
         * 화면 전환 효과는 ±1일 때만 쓴다.
         */
        val pageStep: Int = 0,
        /** 이 작품에 따로 저장된 보기 설정이 있다 (없으면 앱 기본값을 따른다) */
        val seriesCustomized: Boolean = false,
        val showOverlay: Boolean = false,
        val showSettingsPanel: Boolean = false,
        /** 책 끝 화면 (마지막 쪽 다음) — null이면 아님 */
        val endPage: EndPage? = null,
        /** 책 첫 화면 (1쪽 앞, 이전 권으로 가는 곳) — null이면 아님 */
        val startPage: StartPage? = null,
    ) : ReaderUiState {
        /** 보기 설정 패널이 보여 주는 값 */
        val settings: ReaderSettings
            get() = ReaderSettings(
                readerType = readerType,
                pageLayout = pageLayout,
                scaleType = scaleType,
                readingDirection = readingDirection,
                continuousPadding = continuousPadding,
                continuousPageSpacing = continuousPageSpacing,
                imageEnhancement = imageEnhancement,
                zoomPercent = zoomPercent,
                contrastPercent = contrastPercent,
                pageTransition = pageTransition,
            )
    }

    /**
     * 책 끝 화면 (KMReader처럼 다 읽은 책과 다음 권을 나란히). 앞으로 한 번 더 = 다음 권 첫 쪽.
     *
     * @property nextBook 다음 권. 아직 찾는 중이면 [nextResolved]가 false, 마지막 권이면 null
     */
    data class EndPage(
        val finishedTitle: String,
        val finishedThumbnail: String?,
        val nextBook: Book?,
        val nextThumbnail: String?,
        val nextResolved: Boolean,
    )

    /**
     * 책 첫 화면 (1쪽 앞). 책 끝 화면의 거울 — 뒤로 한 번 더 = 이전 권 마지막 쪽.
     *
     * @property previousBook 이전 권. 아직 찾는 중이면 [previousResolved]가 false, 첫 권이면 null
     */
    data class StartPage(
        val currentTitle: String,
        val currentThumbnail: String?,
        val previousBook: Book?,
        val previousThumbnail: String?,
        val previousResolved: Boolean,
    )

    /** 책을 불러오지 못함 — 화면이 오류 종류를 문구로 바꾼다 */
    data class Error(val error: LoadError) : ReaderUiState

    /** EPUB — 페이지 이미지가 아니라 글 리더로 연다 (다음 권으로 넘어온 EPUB이면 그 책) */
    data class Epub(val bookId: String) : ReaderUiState
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

        /** 책 끝(처음)까지 남은 펼침(쪽)이 이 수 이하면 다음(이전) 권을 미리 찾는다 */
        const val NEXT_BOOK_LOOKAHEAD = 3
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
    private var currentReadingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT
    private var layoutOffset: Boolean = false
    private var currentContinuousPadding: Float = 0f
    private var currentContinuousPageSpacing: Int = 0
    private var currentImageEnhancement: ImageEnhancement = ImageEnhancement.OFF
    private var currentZoomPercent: Int = ReaderSettings.MIN_ZOOM_PERCENT
    private var currentContrastPercent: Int = 100
    private var currentPageTransition: PageTransition = PageTransition.SLIDE
    private var pageStep = 0
    private var currentSeriesId: String = ""

    // 다음 권 (KMReader처럼 책 끝 근처에서 미리 찾고 첫 쪽을 미리 받는다)
    private var nextBook: Book? = null
    private var nextBookResolved = false
    private var nextBookJob: Job? = null
    private var showingEndPage = false

    // 이전 권 (책 처음 근처에서 미리 찾고 마지막 쪽을 미리 받는다)
    private var previousBook: Book? = null
    private var previousBookResolved = false
    private var previousBookJob: Job? = null
    private var showingStartPage = false
    private var seriesCustomized = false

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
    fun loadBook(bookId: String) = loadBook(bookId, seamless = false)

    /**
     * @param seamless 다음·이전 권으로 넘어갈 때 — 로딩 화면 없이 지금 화면(책 끝·처음)을 둔 채 불러와 바꾼다
     * @param startAtEnd 이전 권으로 넘어갈 때 — 저장된 진행과 관계없이 마지막 쪽에서 연다
     */
    private fun loadBook(bookId: String, seamless: Boolean, startAtEnd: Boolean = false) {
        if (currentBookId == bookId && _uiState.value is ReaderUiState.Reading) {
            return
        }

        currentBookId = bookId
        hasProgressChanged = false
        saveProgressJob?.cancel()
        nextBookJob?.cancel()
        nextBook = null
        nextBookResolved = false
        showingEndPage = false
        previousBookJob?.cancel()
        previousBook = null
        previousBookResolved = false
        showingStartPage = false
        pageStep = 0

        viewModelScope.launch {
            if (!seamless) _uiState.value = ReaderUiState.Loading

            try {
                val book = bookRepository.getBookById(bookId)
                if (book.mediaProfile == MediaProfile.EPUB) {
                    _uiState.value = ReaderUiState.Epub(bookId)
                    return@launch
                }
                currentBook = book

                val series = seriesRepository.getSeriesById(book.seriesId)
                currentSeriesId = book.seriesId

                // 1~3. 보기 설정: 이 작품에 저장된 설정이 있으면 그것, 없으면 앱 기본값 + 시리즈 메타데이터
                val seriesSettings = readerPreferences.seriesSettings(book.seriesId).first()
                seriesCustomized = seriesSettings != null
                applyToFields(seriesSettings ?: defaultsFor(series.metadata.readingDirection))
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
                val lastPage = currentBookPages.size.coerceAtLeast(1)
                val startPageNumber = if (startAtEnd) lastPage else progressPage?.coerceIn(1, lastPage) ?: 1

                currentSpreadIndex = currentSpreads.indexOfFirst { it.containsPage(startPageNumber) }.coerceAtLeast(0)
                currentContinuousPage = startPageNumber
                firstVisibleTile = null
                continuousCompleted = false
                boundsRequested.clear()
                // 세로 스크롤 맨 위에는 책 첫 카드가 있어 1쪽이어도 늘 스크롤한다
                scrollToTileIndex = if (startAtEnd) bottomScreenTileIndex() else firstTileIndexOf(startPageNumber)
                lastSavedPage = progressPage

                updateReadingState()
                // 처음 근처에서 열면 (8권을 열자마자 7권으로 갈 수 있게) 이전 권을 미리 찾는다
                if (startPageNumber <= 1 + NEXT_BOOK_LOOKAHEAD || currentSpreadIndex <= NEXT_BOOK_LOOKAHEAD) {
                    resolvePreviousBook()
                }
                if (startPageNumber >= lastPage - NEXT_BOOK_LOOKAHEAD) resolveNextBook()
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
        when {
            // 책 첫 화면에서 앞으로 = 1쪽
            showingStartPage -> hideStartPage()
            // 책 끝 화면에서 앞으로 = 다음 권
            showingEndPage -> openNextBook()
            // 마지막 펼침에서 앞으로 = 책 끝 화면 (마지막 쪽 진행·완독은 이미 저장 예약됨)
            currentSpreadIndex >= currentSpreads.lastIndex -> showEndPage()
            else -> applySpreadChange(currentSpreadIndex + 1, step = 1)
        }
    }

    /** Paged: 이전 펼침으로 이동 (책 끝 화면이면 마지막 펼침으로) */
    fun previousSpread() {
        if (showingEndPage) {
            showingEndPage = false
            updateReadingState()
            return
        }
        when {
            // 책 첫 화면에서 뒤로 = 이전 권 마지막 쪽
            showingStartPage -> openPreviousBook()
            // 첫 펼침에서 뒤로 = 책 첫 화면
            currentSpreadIndex <= 0 -> showStartPage()
            else -> applySpreadChange(currentSpreadIndex - 1, step = -1)
        }
    }

    /** 책 첫 화면을 보인다 — 이전 권을 아직 안 찾았으면 지금 찾는다 */
    fun showStartPage() {
        showingStartPage = true
        resolvePreviousBook()
        updateReadingState()
    }

    private fun hideStartPage() {
        showingStartPage = false
        updateReadingState()
    }

    /** 이전 권을 같은 화면에서 마지막 쪽부터 연다 (지금 책 진행을 먼저 바로 저장) */
    fun openPreviousBook() {
        val previous = previousBook ?: return
        saveProgressOnExit()
        loadBook(previous.id, seamless = true, startAtEnd = true)
    }

    /** 이전 권을 한 번만 찾고, 찾으면 마지막 쪽을 미리 받게 한다. 책 처음 근처에서 부른다. */
    private fun resolvePreviousBook() {
        if (previousBookResolved || previousBookJob?.isActive == true) return
        val bookId = currentBookId
        previousBookJob = viewModelScope.launch {
            val found = try {
                bookRepository.getPreviousBook(bookId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "이전 권 찾기 실패: $bookId")
                null
            }
            if (bookId != currentBookId) return@launch
            previousBook = found
            previousBookResolved = true
            if (found != null && found.pageCount > 0 && currentReaderType == ReaderType.CONTINUOUS) {
                runCatching { tileSource.prefetch(found.id, found.pageCount) }
            }
            updateReadingState()
        }
    }

    /** 책 끝 화면을 보인다 — 다음 권을 아직 안 찾았으면 지금 찾는다 */
    fun showEndPage() {
        showingEndPage = true
        resolveNextBook()
        updateReadingState()
    }

    /** 다음 권을 같은 화면에서 이어 연다 (지금 책 진행을 먼저 바로 저장) */
    fun openNextBook() {
        val next = nextBook ?: return
        saveProgressOnExit()
        loadBook(next.id, seamless = true)
    }

    /**
     * 다음 권을 한 번만 찾고, 찾으면 첫 쪽들을 미리 받게 한다 (KMReader의 다음 세그먼트 미리 불러오기).
     * 책 끝 근처(남은 펼침 [NEXT_BOOK_LOOKAHEAD] 이하)에서 부른다.
     */
    private fun resolveNextBook() {
        if (nextBookResolved || nextBookJob?.isActive == true) return
        val bookId = currentBookId
        nextBookJob = viewModelScope.launch {
            val found = try {
                bookRepository.getNextBook(bookId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "다음 권 찾기 실패: $bookId")
                null
            }
            if (bookId != currentBookId) return@launch
            nextBook = found
            nextBookResolved = true
            if (found != null && currentReaderType == ReaderType.CONTINUOUS) {
                runCatching { tileSource.prefetch(found.id, 1) }
            }
            updateReadingState()
        }
    }

    /** Continuous: 맨 위 타일이 쪽 중간이면 그 쪽 처음, 쪽 처음이면 이전 쪽 처음으로 */
    private fun previousContinuousPage() {
        if (showingStartPage) {
            openPreviousBook()
            return
        }
        val top = firstVisibleTile ?: return
        val target = if (top.tileIndex > 0) top.pageNumber else top.pageNumber - 1
        if (target >= 1) {
            scrollToPage(target)
        } else {
            // 1쪽 처음에서 이전 = 책 첫 카드로
            scrollToTileIndex = -1
            showStartPage()
        }
    }

    /** Continuous: 맨 위에 보이는 쪽의 다음 쪽 처음으로 */
    private fun nextContinuousPage() {
        if (showingStartPage) {
            showingStartPage = false
            scrollToPage(1)
            return
        }
        if (showingEndPage) {
            openNextBook()
            return
        }
        val top = firstVisibleTile?.pageNumber ?: currentContinuousPage
        if (top < currentBookPages.size) {
            scrollToPage(top + 1)
        } else {
            // 마지막 쪽에서 다음 = 책 끝 카드로
            scrollToTileIndex = currentTiles.size
            showEndPage()
        }
    }

    /** Continuous: 맨 아래 책 끝 카드가 화면에 보이는지 */
    fun onContinuousEndVisible(visible: Boolean) {
        if (currentReaderType != ReaderType.CONTINUOUS || visible == showingEndPage) return
        if (visible) showEndPage() else {
            showingEndPage = false
            updateReadingState()
        }
    }

    /** Continuous: 맨 위 책 첫 카드가 화면에 보이는지 */
    fun onContinuousStartVisible(visible: Boolean) {
        if (currentReaderType != ReaderType.CONTINUOUS || visible == showingStartPage) return
        // 처음 열 때 목록이 시작 위치로 스크롤하기 전에는 첫 카드가 잠깐 보인다 — 무시
        if (visible && scrollToTileIndex != null && scrollToTileIndex != -1) return
        if (visible) showStartPage() else hideStartPage()
    }

    /** 책 마지막이 화면 맨 아래에 오게 하는 타일 (이전 권으로 넘어와 끝에서 열 때) */
    private fun bottomScreenTileIndex(): Int {
        var height = 0
        for (i in currentTiles.indices.reversed()) {
            height += currentTiles[i].displayHeight
            if (height >= screenHeightPx) return i
        }
        return 0
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
                applySpreadChange(targetSpreadIndex, step = 0)
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
        setReaderTypeWithoutSaving(type)
        saveSeriesSettings()
    }

    private fun setReaderTypeWithoutSaving(type: ReaderType) {
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
    }

    /** 쪽 배치(PageLayout) 변경 */
    fun setPageLayout(layout: PageLayout) {
        if (currentLayout == layout) return
        currentLayout = layout
        rebuildSpreadsAndRetainPage()
        saveSeriesSettings()
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
        saveSeriesSettings()
    }

    /** 읽기 방향(ReadingDirection) 변경 */
    fun setReadingDirection(direction: ReadingDirection) {
        currentReadingDirection = direction
        updateReadingState()
        saveSeriesSettings()
    }

    /** 화질 개선 변경 */
    fun setImageEnhancement(enhancement: ImageEnhancement) {
        if (currentImageEnhancement == enhancement) return
        currentImageEnhancement = enhancement
        updateReadingState()
        saveSeriesSettings()
    }

    /** 보기 설정 패널에서 바꾼 값 — 바뀐 항목만 반영하고 이 작품에 저장한다 */
    fun applySettings(new: ReaderSettings) {
        if (new.readerType != currentReaderType) setReaderType(new.readerType)
        if (new.pageLayout != currentLayout) setPageLayout(new.pageLayout)
        if (new.readingDirection != currentReadingDirection) setReadingDirection(new.readingDirection)
        if (new.scaleType != currentScaleType) setScaleType(new.scaleType)
        if (new.continuousPadding != currentContinuousPadding) setContinuousPadding(new.continuousPadding)
        if (new.continuousPageSpacing != currentContinuousPageSpacing) setContinuousPageSpacing(new.continuousPageSpacing)
        if (new.imageEnhancement != currentImageEnhancement) setImageEnhancement(new.imageEnhancement)
        if (new.zoomPercent != currentZoomPercent) setZoomPercent(new.zoomPercent)
        if (new.contrastPercent != currentContrastPercent) setContrastPercent(new.contrastPercent)
        if (new.pageTransition != currentPageTransition) setPageTransition(new.pageTransition)
    }

    /** 화면 전환 효과 변경 — 이 작품에 저장 */
    fun setPageTransition(transition: PageTransition) {
        if (currentPageTransition == transition) return
        currentPageTransition = transition
        updateReadingState()
        saveSeriesSettings()
    }

    /** 대비 변경 — 이 작품에 저장 */
    fun setContrastPercent(percent: Int) {
        val clamped = percent.coerceIn(ReaderSettings.MIN_CONTRAST_PERCENT, ReaderSettings.MAX_CONTRAST_PERCENT)
        if (currentContrastPercent == clamped) return
        currentContrastPercent = clamped
        updateReadingState()
        saveSeriesSettings()
    }

    /** 확대(줌인) 비율 변경 — 이 작품에 저장 */
    fun setZoomPercent(percent: Int) {
        val clamped = percent.coerceIn(ReaderSettings.MIN_ZOOM_PERCENT, ReaderSettings.MAX_ZOOM_PERCENT)
        if (currentZoomPercent == clamped) return
        currentZoomPercent = clamped
        updateReadingState()
        saveSeriesSettings()
    }

    /** 이 작품의 보기 설정을 지우고 앱 기본값으로 되돌린다 (보던 쪽은 그대로) */
    fun resetSeriesSettings() {
        val seriesId = currentSeriesId.ifEmpty { return }
        viewModelScope.launch {
            readerPreferences.clearSeriesSettings(seriesId)
            seriesCustomized = false
            val metadataDirection = runCatching { seriesRepository.getSeriesById(seriesId).metadata.readingDirection }
                .getOrNull()
            val defaults = defaultsFor(metadataDirection)
            val keepType = currentReaderType
            applyToFields(defaults.copy(readerType = keepType))
            // 펼침·타일을 새 설정으로 다시 만들되 보던 쪽은 그대로
            rebuildSpreadsAndRetainPage()
            rebuildContinuousTiles()
            // 리더 종류가 바뀌면 보던 쪽에서 이어 보게
            if (defaults.readerType != keepType) setReaderTypeWithoutSaving(defaults.readerType)
            updateReadingState()
        }
    }

    /** 앱 기본값 + 시리즈 메타데이터 규칙 (Komelia와 같음) */
    private suspend fun defaultsFor(metadataDirection: ReadingDirection?): ReaderSettings {
        val global = readerPreferences.readerSettings.first()
        return global.copy(
            readerType = ReaderTypeResolver.resolveReaderType(metadataDirection, global.readerType),
            readingDirection = metadataDirection
                ?.takeIf { it == ReadingDirection.LEFT_TO_RIGHT || it == ReadingDirection.RIGHT_TO_LEFT }
                ?: global.readingDirection,
        )
    }

    private fun applyToFields(settings: ReaderSettings) {
        currentReaderType = settings.readerType
        currentReadingDirection = settings.readingDirection
        currentLayout = settings.pageLayout
        currentScaleType = settings.scaleType
        currentContinuousPadding = settings.continuousPadding
        currentContinuousPageSpacing = settings.continuousPageSpacing
        currentImageEnhancement = settings.imageEnhancement
        currentZoomPercent = settings.zoomPercent
        currentContrastPercent = settings.contrastPercent
        currentPageTransition = settings.pageTransition
    }

    private fun currentSettings() = ReaderSettings(
        readerType = currentReaderType,
        pageLayout = currentLayout,
        scaleType = currentScaleType,
        readingDirection = currentReadingDirection,
        continuousPadding = currentContinuousPadding,
        continuousPageSpacing = currentContinuousPageSpacing,
        imageEnhancement = currentImageEnhancement,
        zoomPercent = currentZoomPercent,
        contrastPercent = currentContrastPercent,
        pageTransition = currentPageTransition,
    )

    /** 패널에서 바꾼 값은 이 작품(시리즈)에만 저장한다 — 앱 기본값은 서버 설정 화면에서 */
    private fun saveSeriesSettings() {
        val seriesId = currentSeriesId.ifEmpty { return }
        val snapshot = currentSettings()
        if (!seriesCustomized) {
            seriesCustomized = true
            updateReadingState()
        }
        viewModelScope.launch { readerPreferences.saveSeriesSettings(seriesId, snapshot) }
    }

    /** 세로 스크롤 좌우 여백(continuousPadding) 변경 */
    fun setContinuousPadding(padding: Float) {
        if (currentContinuousPadding == padding) return
        currentContinuousPadding = padding
        rebuildContinuousTiles()
        updateReadingState()
        saveSeriesSettings()
    }

    /** 세로 스크롤 쪽 사이 간격(continuousPageSpacing) 변경 */
    fun setContinuousPageSpacing(spacing: Int) {
        if (currentContinuousPageSpacing == spacing) return
        currentContinuousPageSpacing = spacing
        rebuildContinuousTiles()
        updateReadingState()
        saveSeriesSettings()
    }

    /** Continuous: 타일 이미지 (화면이 보이는 타일마다 요청) */
    suspend fun loadTile(tile: PageTile): ImageBitmap =
        tileSource.tile(
            currentBookId, tile, ImageEnhancer.sharpenAmount(currentImageEnhancement), ImageEnhancer.lanczos(currentImageEnhancement),
        )

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
        if (progress.pageNumber >= totalPages - NEXT_BOOK_LOOKAHEAD) resolveNextBook()
        if (visible.first().pageNumber <= 1 + NEXT_BOOK_LOOKAHEAD) resolvePreviousBook()
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

    /** @param step +1 다음 펼침, −1 이전 펼침, 0 점프 — 화면 전환 효과 방향 */
    private fun applySpreadChange(newSpreadIndex: Int, step: Int) {
        pageStep = step
        if (newSpreadIndex >= currentSpreads.lastIndex - NEXT_BOOK_LOOKAHEAD) resolveNextBook()
        if (newSpreadIndex <= NEXT_BOOK_LOOKAHEAD) resolvePreviousBook()
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

        pageStep = 0
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
        val spreadUrls = currentSpread.pages.mapNotNull { pagedUrl(book, it.number) }
        val preloadSpreads = buildPreloadSpreads(currentBookId, currentSpreadIndex, currentSpreads) +
            nextBookPreload() + previousBookPreload()

        val existingOverlay = (_uiState.value as? ReaderUiState.Reading)?.showOverlay ?: false
        val existingSettings = (_uiState.value as? ReaderUiState.Reading)?.showSettingsPanel ?: false

        _uiState.value = ReaderUiState.Reading(
            bookTitle = book.displayName(),
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
            imageEnhancement = currentImageEnhancement,
            zoomPercent = currentZoomPercent,
            contrastPercent = currentContrastPercent,
            pageTransition = currentPageTransition,
            pageStep = pageStep,
            seriesCustomized = seriesCustomized,
            showOverlay = existingOverlay,
            showSettingsPanel = existingSettings,
            endPage = if (showingEndPage) {
                ReaderUiState.EndPage(
                    finishedTitle = book.displayName(),
                    finishedThumbnail = imageUrls.bookThumbnail(book.id),
                    nextBook = nextBook,
                    nextThumbnail = nextBook?.let { imageUrls.bookThumbnail(it.id) },
                    nextResolved = nextBookResolved,
                )
            } else {
                null
            },
            startPage = if (showingStartPage) {
                ReaderUiState.StartPage(
                    currentTitle = book.displayName(),
                    currentThumbnail = imageUrls.bookThumbnail(book.id),
                    previousBook = previousBook,
                    previousThumbnail = previousBook?.let { imageUrls.bookThumbnail(it.id) },
                    previousResolved = previousBookResolved,
                )
            } else {
                null
            },
        )
    }

    /** 마지막 펼침 근처면 다음 권 첫 두 쪽도 미리 받는다 — 넘어갈 때 바로 보이게 */
    private fun nextBookPreload(): List<List<String>> {
        val next = nextBook ?: return emptyList()
        if (currentReaderType != ReaderType.PAGED) return emptyList()
        if (currentSpreadIndex < currentSpreads.lastIndex - 1 && !showingEndPage) return emptyList()
        return listOf(listOfNotNull(pagedUrl(next, 1)), listOfNotNull(pagedUrl(next, 2)))
    }

    /** 페이지 넘김 모드의 쪽 주소 — PDF 책은 서버가 그리지 않은 원래 쪽(TV에서 그린다) */
    private fun pagedUrl(book: Book, pageNumber: Int): String? =
        if (book.mediaProfile == MediaProfile.PDF) {
            imageUrls.pdfPage(book.id, pageNumber)
        } else {
            imageUrls.page(book.id, pageNumber)
        }

    /** 첫 펼침 근처면 이전 권 마지막 두 쪽도 미리 받는다 */
    private fun previousBookPreload(): List<List<String>> {
        val previous = previousBook ?: return emptyList()
        if (currentReaderType != ReaderType.PAGED || previous.pageCount <= 0) return emptyList()
        if (currentSpreadIndex > 1 && !showingStartPage) return emptyList()
        return listOf(previous.pageCount, previous.pageCount - 1)
            .filter { it >= 1 }
            .map { listOfNotNull(pagedUrl(previous, it)) }
    }

    private fun buildPreloadSpreads(
        bookId: String,
        spreadIndex: Int,
        spreads: List<PageSpread>,
    ): List<List<String>> {
        // 빨리 넘길 때 검은 화면이 덜 보이게 앞쪽을 더 멀리, 가까운 것부터 받는다
        val targetIndices = listOf(
            spreadIndex + 1,
            spreadIndex + 2,
            spreadIndex - 1,
            spreadIndex + 3,
            spreadIndex + 4,
            spreadIndex - 2,
        ).filter { it in spreads.indices }

        return targetIndices.map { idx ->
            spreads[idx].pages.mapNotNull { page ->
                currentBook?.let { pagedUrl(it, page.number) } ?: imageUrls.page(bookId, page.number)
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
