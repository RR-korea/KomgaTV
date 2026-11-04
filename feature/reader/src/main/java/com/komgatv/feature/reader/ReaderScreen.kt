package com.komgatv.feature.reader

import android.content.Context
import androidx.activity.compose.BackHandler
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import timber.log.Timber
import com.komgatv.core.model.ImageEnhancement
import com.komgatv.core.model.PageTransition
import com.komgatv.core.model.ReaderType
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import com.komgatv.core.ui.components.ProgressBar
import com.komgatv.core.ui.components.loadErrorText
import com.komgatv.feature.reader.continuous.ContinuousPages
import com.komgatv.feature.reader.enhance.ImageEnhancer
import com.komgatv.feature.reader.enhance.LanczosTransformation
import com.komgatv.feature.reader.enhance.SharpenTransformation
import coil3.request.transformations
import com.komgatv.feature.reader.epub.EpubReaderScreen
import kotlinx.coroutines.launch
import coil3.size.Size as CoilSize

/** 너비 맞춤·원본 크기에서 디코딩할 최대 높이 = 화면 높이 × 이 값 (긴 페이지가 메모리를 다 쓰지 않게) */
private const val MAX_TALL_SCREENS = 3

/**
 * 전체 화면 만화 페이지 리더
 *
 * 리모컨 키 (오버레이 닫힘):
 * - 좌/우: Paged = 이전/다음 펼침 (RIGHT_TO_LEFT면 반대), Continuous = 이전/다음 쪽 처음
 * - 위/아래: Paged = 화면보다 큰 페이지(너비 맞춤·원본)를 화면 절반씩, Continuous = 화면 높이만큼 스크롤 (Komelia와 같음)
 * - OK: 오버레이 열기
 * - Back: 진행 저장 후 종료
 *
 * 리모컨 키 (오버레이 열림):
 * - 좌/우: 펼침 이동, 위/아래: 10쪽 점프
 * - OK: 보기 설정 패널 열기
 * - Back: 오버레이 닫기
 *
 * 보기 설정 패널: 위/아래 항목 이동, 좌/우·OK 값 바꾸기, Back 패널 닫기(오버레이로 돌아감).
 *
 * @param bookId 읽을 책 ID
 * @param onNavigateBack 뒤로가기 콜백
 * @param viewModel 리더 ViewModel
 */
@Composable
fun ReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveProgressOnExit()
        }
    }

    when (val state = uiState) {
        is ReaderUiState.Loading -> CenteredMessage(stringResource(R.string.reader_loading_page), Color.White)

        is ReaderUiState.Error -> CenteredMessage(
            stringResource(R.string.reader_error_prefix, loadErrorText(state.error)),
            Color.Red,
        )

        // 다음 권으로 이어 온 EPUB이면 그 책을 연다
        is ReaderUiState.Epub -> EpubReaderScreen(bookId = state.bookId, onNavigateBack = onNavigateBack)

        is ReaderUiState.Reading -> {
            ReaderContent(
                state = state,
                viewModel = viewModel,
                onNavigateBack = {
                    viewModel.saveProgressOnExit()
                    onNavigateBack()
                },
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * 리더 콘텐츠 (펼침 표시, 프리로드, 스크롤, 오버레이, 설정 패널, 키 처리)
 */
@Composable
private fun ReaderContent(
    state: ReaderUiState.Reading,
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidthPx = containerSize.width
    val screenHeightPx = containerSize.height

    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val continuous = state.readerType == ReaderType.CONTINUOUS
    val scrollable = !continuous && (state.scaleType == ScaleType.FIT_WIDTH || state.scaleType == ScaleType.ORIGINAL)
    // 맨 위(0번)는 책 첫 카드 — 처음엔 그 아래 첫 타일에서
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 1)

    // 세로 스크롤 타일은 화면 크기로 계획한다
    LaunchedEffect(screenWidthPx, screenHeightPx) {
        viewModel.setScreenSize(screenWidthPx, screenHeightPx)
    }

    // 처음 그릴 때와 설정 패널이 닫힐 때 리더가 키를 받도록 포커스를 가져온다
    LaunchedEffect(state.showSettingsPanel) {
        if (!state.showSettingsPanel) focusRequester.requestFocus()
    }

    // 펼침이나 크기 맞춤이 바뀌면 맨 위에서 시작
    LaunchedEffect(state.currentSpreadIndex, state.scaleType) {
        scrollState.scrollTo(0)
    }

    // 이웃 펼침 미리 받기 — 실제 표시와 같은 요청(크기·정밀도)이라야 메모리 캐시에 맞는다.
    // 더는 가까이 있지 않은 쪽은 취소한다: 빨리 넘기거나 10쪽씩 건너뛰어도 받을 쪽이 쌓이지 않게
    // (PDF는 서버가 쪽마다 2~3초씩 그려서, 쌓이면 지금 쪽이 그 뒤에서 한참 기다린다)
    val preloads = remember { mutableMapOf<String, Disposable>() }
    LaunchedEffect(
        state.preloadSpreads, state.spreadPageUrls, state.scaleType, state.imageEnhancement, screenWidthPx, screenHeightPx,
    ) {
        val imageLoader = SingletonImageLoader.get(context)
        val variant = "${state.scaleType}|${state.imageEnhancement}|${screenWidthPx}x$screenHeightPx"
        val wanted = state.preloadSpreads.flatMap { urls -> urls.map { url -> "$url|${urls.size}|$variant" to (url to urls.size) } }
            .toMap()
        // 지금 보이는 쪽은 화면이 같은 주소를 받는 중일 수 있어 끊지 않는다 (같은 요청은 한 번만 받는다)
        val current = state.spreadPageUrls.map { "$it|${state.spreadPageUrls.size}|$variant" }.toSet()
        preloads.entries.removeAll { (key, disposable) ->
            val drop = disposable.isDisposed || (key !in wanted && key !in current)
            if (drop) disposable.dispose()
            drop
        }
        wanted.forEach { (key, page) ->
            if (key in preloads) return@forEach
            val (url, slots) = page
            preloads[key] = imageLoader.enqueue(
                pageRequest(context, url, state.scaleType, slotWidth(screenWidthPx, slots), screenHeightPx, state.imageEnhancement)
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            preloads.values.forEach { it.dispose() }
            preloads.clear()
        }
    }

    // Back은 키 이벤트로 오지 않는다 (targetSdk 36: 예측형 뒤로 가기). 시스템 뒤로 가기를 가로챈다.
    BackHandler {
        when {
            state.showSettingsPanel -> viewModel.closeSettingsPanel()
            state.showOverlay -> viewModel.closeOverlay()
            else -> onNavigateBack()
        }
    }

    val contentScale = remember(state.scaleType) { contentScaleFor(state.scaleType) }

    // 확대(줌인): 크기 맞춤 결과를 펼침째(두 쪽이면 두 쪽 함께, 가운데 기준) 키운다.
    // 화면 고정 모드에서는 ▲▼로 위아래를 본다 (반 칸씩, 쪽이 바뀌면 가운데로).
    val zoom = state.zoomPercent / 100f
    var panStep by remember(state.currentSpreadIndex, state.zoomPercent, state.scaleType) { mutableIntStateOf(0) }
    val panOffset by animateFloatAsState(
        targetValue = -panStep / PAN_STEPS.toFloat() * (zoom - 1f) * screenHeightPx / 2f,
        label = "zoom-pan",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // 설정 패널이 열려 있으면 키는 패널 항목이 처리
                if (state.showSettingsPanel) return@onPreviewKeyEvent false

                when (keyEvent.key) {
                    Key.DirectionLeft -> viewModel.onDpadLeft()
                    Key.DirectionRight -> viewModel.onDpadRight()
                    Key.DirectionCenter, Key.Enter -> when {
                        state.showOverlay -> viewModel.openSettingsPanel()
                        // 책 끝 화면에서 OK = 다음 권, 책 첫 화면에서 OK = 이전 권
                        state.endPage != null -> viewModel.openNextBook()
                        state.startPage != null -> viewModel.openPreviousBook()
                        else -> viewModel.toggleOverlay()
                    }
                    Key.DirectionUp -> if (state.showOverlay) {
                        viewModel.jumpPages(10)
                    } else if (continuous) {
                        // 책 첫 카드까지 올라왔으면 위 = 이전 권
                        if (state.startPage != null && !listState.canScrollBackward) {
                            viewModel.openPreviousBook()
                        } else {
                            coroutineScope.launch { listState.animateScrollBy(-screenHeightPx.toFloat()) }
                        }
                    } else if (scrollable && scrollState.value > 0) {
                        coroutineScope.launch {
                            scrollState.animateScrollTo((scrollState.value - screenHeightPx / 2).coerceAtLeast(0))
                        }
                    } else if (!scrollable && zoom > 1f) {
                        panStep = (panStep - 1).coerceAtLeast(-PAN_STEPS)
                    }
                    Key.DirectionDown -> if (state.showOverlay) {
                        viewModel.jumpPages(-10)
                    } else if (continuous) {
                        // 책 끝 카드까지 내려왔으면 아래 = 다음 권
                        if (state.endPage != null && !listState.canScrollForward) {
                            viewModel.openNextBook()
                        } else {
                            coroutineScope.launch { listState.animateScrollBy(screenHeightPx.toFloat()) }
                        }
                    } else if (scrollable && scrollState.value < scrollState.maxValue) {
                        coroutineScope.launch {
                            scrollState.animateScrollTo(
                                (scrollState.value + screenHeightPx / 2).coerceAtMost(scrollState.maxValue)
                            )
                        }
                    } else if (!scrollable && zoom > 1f) {
                        panStep = (panStep + 1).coerceAtMost(PAN_STEPS)
                    }
                    else -> return@onPreviewKeyEvent false
                }
                // 리더가 받는 방향키는 끝에 닿아도 소비한다 (포커스가 엉뚱한 곳으로 가지 않게)
                true
            },
    ) {
        val spreadContent: @Composable () -> Unit = {
            AnimatedSpread(state) { shown ->
                SpreadPages(
                    state = shown,
                    contentScale = contentScale,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    scrollable = scrollable,
                )
            }
        }

        if (!continuous && state.endPage != null) {
            EndPageView(state.endPage, BoundaryLayout.of(toNext = true, state.readingDirection, continuous = false), Modifier.fillMaxSize())
        } else if (!continuous && state.startPage != null) {
            StartPageView(state.startPage, BoundaryLayout.of(toNext = false, state.readingDirection, continuous = false), Modifier.fillMaxSize())
        } else if (continuous) {
            ContinuousPages(
                tiles = state.continuousTiles,
                listState = listState,
                filterQuality = ImageEnhancer.filterQuality(state.imageEnhancement),
                colorFilter = ImageEnhancer.contrastFilter(state.contrastPercent),
                scrollToTileIndex = state.scrollToTileIndex,
                // 화질 설정이 바뀌면 보이는 타일도 다시 불러오게 함수 자체를 바꾼다
                loadTile = remember(state.imageEnhancement) { { tile -> viewModel.loadTile(tile) } },
                onScrolled = viewModel::onTileScrolled,
                onVisibleTilesChanged = viewModel::onContinuousVisibleTilesChanged,
                endPage = state.endPage,
                onEndVisible = viewModel::onContinuousEndVisible,
                startPage = state.startPage,
                onStartVisible = viewModel::onContinuousStartVisible,
            )
        } else if (scrollable) {
            // 화면보다 클 수 있는 모드: 세로 스크롤, 작은 페이지는 가운데
            val screenHeightDp = with(LocalDensity.current) { screenHeightPx.toDp() }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zoomedFromTop(zoom)
                        .heightIn(min = screenHeightDp),
                    contentAlignment = Alignment.Center,
                ) {
                    spreadContent()
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationY = panOffset
                    },
                contentAlignment = Alignment.Center,
            ) {
                spreadContent()
            }
        }

        // 정보 오버레이
        AnimatedVisibility(
            visible = state.showOverlay && !state.showSettingsPanel,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ReaderOverlay(state)
        }

        // 보기 설정 패널
        AnimatedVisibility(
            visible = state.showSettingsPanel,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ReaderSettingsPanel(
                settings = state.settings,
                layoutOffset = state.layoutOffset,
                seriesCustomized = state.seriesCustomized,
                onChange = viewModel::applySettings,
                onLayoutOffsetChange = viewModel::setLayoutOffset,
                onReset = viewModel::resetSeriesSettings,
                hidablePages = state.hidablePages,
                hiddenPageCount = state.hiddenPageCount,
                onHidePage = viewModel::hidePage,
                onRestoreHiddenPages = viewModel::restoreHiddenPages,
            )
        }
    }
}

/** 이보다 빨리 연달아 넘기면 효과 없이 바로 바꾼다 (키를 누르고 있을 때 오히려 느려지지 않게) */
private const val RAPID_FLIP_MILLIS = 300L
private const val SLIDE_MILLIS = 180
private const val FADE_MILLIS = 150

/**
 * 펼침이 바뀔 때 화면 전환 효과 — 이미 그린 화면을 옮기거나(밀기) 겹치기만(페이드) 해서 GPU 부담이 적다.
 *
 * 효과는 한 칸 앞·뒤로 넘길 때만, 읽기 방향을 따라간다(오른쪽→왼쪽이면 반대로 민다).
 * 점프·책 바꿈·설정 변경, 빠른 연타, 아직 받지 않은 쪽(불러오는 중 표시)은 바로 바꾼다.
 */
@Composable
private fun AnimatedSpread(state: ReaderUiState.Reading, content: @Composable (ReaderUiState.Reading) -> Unit) {
    val context = LocalContext.current
    // 효과는 펼침이 바뀔 때 한 번만 정한다 — transitionSpec은 같은 전환에도 여러 번 불린다
    val decision = remember { TransitionDecision() }
    AnimatedContent(
        targetState = state,
        contentKey = { it.spreadPageUrls },
        transitionSpec = {
            val target = targetState
            if (target.spreadPageUrls != decision.urls) {
                val now = SystemClock.uptimeMillis()
                val rapid = now - decision.at < RAPID_FLIP_MILLIS
                val ready = target.spreadPageUrls.all { url -> isInMemory(context, url) }
                decision.urls = target.spreadPageUrls
                decision.at = now
                decision.animate = target.pageTransition != PageTransition.OFF && target.pageStep != 0 && !rapid && ready
                Timber.d("펼침 전환: step=${target.pageStep} rapid=$rapid ready=$ready → ${if (decision.animate) target.pageTransition else "없음"}")
            }
            when {
                !decision.animate -> EnterTransition.None togetherWith ExitTransition.None
                target.pageTransition == PageTransition.FADE ->
                    fadeIn(tween(FADE_MILLIS)) togetherWith fadeOut(tween(FADE_MILLIS))
                else -> {
                    // 다음 펼침은 읽는 쪽에서 들어온다: 왼쪽→오른쪽이면 오른쪽에서, 오른쪽→왼쪽이면 왼쪽에서
                    val rtl = target.readingDirection == ReadingDirection.RIGHT_TO_LEFT
                    val from = if ((target.pageStep > 0) != rtl) 1 else -1
                    slideInHorizontally(tween(SLIDE_MILLIS)) { width -> from * width } togetherWith
                        slideOutHorizontally(tween(SLIDE_MILLIS)) { width -> -from * width }
                }
            }.using(SizeTransform(clip = true))
        },
        label = "spread",
    ) { shown ->
        content(shown)
    }
}

/** 마지막으로 정한 펼침 전환 (어느 펼침으로, 언제, 효과를 쓸지) */
private class TransitionDecision {
    var urls: List<String> = emptyList()
    var at = 0L
    var animate = false
}

/** 이 쪽이 이미 메모리에 있어 바로 그릴 수 있는지 */
private fun isInMemory(context: Context, url: String): Boolean {
    val cache = SingletonImageLoader.get(context).memoryCache ?: return false
    return cache.keys.any { it.key == url }
}

/**
 * 현재 펼침의 1~2쪽. 두 쪽이면 화면을 반씩 나누고, RIGHT_TO_LEFT면 앞 쪽이 오른쪽.
 */
@Composable
private fun SpreadPages(
    state: ReaderUiState.Reading,
    contentScale: ContentScale,
    screenWidthPx: Int,
    screenHeightPx: Int,
    scrollable: Boolean,
) {
    val context = LocalContext.current
    val urls = if (state.readingDirection == ReadingDirection.RIGHT_TO_LEFT) {
        state.spreadPageUrls.reversed()
    } else {
        state.spreadPageUrls
    }
    if (urls.isEmpty()) return

    val slotWidthPx = slotWidth(screenWidthPx, urls.size)
    // 스크롤 모드는 높이를 이미지에 맡기고(너비 기준), 고정 모드는 칸을 꽉 채운 뒤 contentScale로 맞춘다
    val pageModifier = if (scrollable) Modifier.fillMaxWidth() else Modifier.fillMaxSize()

    Row(
        modifier = if (scrollable) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        urls.forEachIndexed { index, url ->
            // 두 쪽이면 가운데(접히는 곳)에 붙인다
            val alignment = when {
                urls.size < 2 -> Alignment.Center
                index == 0 -> Alignment.CenterEnd
                else -> Alignment.CenterStart
            }
            // PDF 원본 쪽(/raw)을 서버가 못 주면(파일을 옮겼거나 분석 중 등) 서버가 그린 일반 쪽으로 다시 받는다
            var useRendered by remember(url) { mutableStateOf(false) }
            val pageUrl = if (useRendered) url.removeSuffix("/raw") else url
            val request = remember(pageUrl, state.scaleType, slotWidthPx, screenHeightPx, state.imageEnhancement) {
                pageRequest(context, pageUrl, state.scaleType, slotWidthPx, screenHeightPx, state.imageEnhancement)
            }
            // 받는 중이면 스피너, 실패하면 문구 — 빨리 넘길 때 검은 화면이 멈춘 것처럼 보이지 않게
            var loadState by remember(request) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(if (scrollable) Modifier else Modifier.fillMaxHeight())
                    .then(pageModifier),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = stringResource(
                        R.string.reader_page_content_description,
                        state.currentSpread.highestPageNumber,
                        state.totalPages,
                    ),
                    contentScale = contentScale,
                    alignment = alignment,
                    filterQuality = ImageEnhancer.filterQuality(state.imageEnhancement),
                    colorFilter = ImageEnhancer.contrastFilter(state.contrastPercent),
                    onState = {
                        loadState = it
                        if (it is AsyncImagePainter.State.Error && !useRendered && url.endsWith("/raw")) useRendered = true
                    },
                    modifier = pageModifier,
                )
                when (loadState) {
                    is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty -> PageLoadingIndicator()
                    is AsyncImagePainter.State.Error -> PageLoadFailed()
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ReaderOverlay(state: ReaderUiState.Reading) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            Text(
                text = state.bookTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val pageNumbers = if (state.readerType == ReaderType.CONTINUOUS) {
                    state.currentContinuousPage.toString()
                } else {
                    state.currentSpread.pages.map { it.number }.sorted().joinToString("-")
                }
                Text(
                    text = stringResource(R.string.reader_page_range_format, pageNumbers, state.totalPages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                val percent = state.shownPage * 100 / state.totalPages
                Text(
                    text = stringResource(R.string.reader_page_percent_format, percent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ProgressBar(
                progress = state.shownPage.toFloat() / state.totalPages,
                // 오른쪽에서 왼쪽으로 읽으면 진행도 오른쪽에서 왼쪽으로 찬다 (Komga 웹 리더와 같음)
                fromEnd = state.readerType == ReaderType.PAGED && state.readingDirection == ReadingDirection.RIGHT_TO_LEFT,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.reader_overlay_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

private fun slotWidth(screenWidthPx: Int, pagesInSpread: Int): Int =
    if (pagesInSpread >= 2) screenWidthPx / 2 else screenWidthPx

/**
 * 페이지 이미지 요청. 디코딩 크기는 맞춤 방식에 따라 정한다 (원본 그대로 디코딩 금지 — 2GB TV).
 * INEXACT라서 디코딩 단계에서는 키우지 않는다. 확대는 그릴 때 contentScale이 한다.
 */
private fun pageRequest(
    context: Context,
    url: String,
    scaleType: ScaleType,
    slotWidthPx: Int,
    screenHeightPx: Int,
    enhancement: ImageEnhancement,
): ImageRequest {
    val size = when (scaleType) {
        ScaleType.SCREEN -> CoilSize(slotWidthPx, screenHeightPx)
        // 높이 맞춤: 가로가 칸보다 넓어질 수 있다 (펼침 스캔 등)
        ScaleType.FIT_HEIGHT -> CoilSize(slotWidthPx * 2, screenHeightPx)
        ScaleType.FIT_WIDTH -> CoilSize(slotWidthPx, screenHeightPx * MAX_TALL_SCREENS)
        ScaleType.ORIGINAL -> CoilSize(slotWidthPx * 2, screenHeightPx * MAX_TALL_SCREENS)
    }
    return ImageRequest.Builder(context)
        .data(url)
        .size(size)
        .precision(Precision.INEXACT)
        .crossfade(false)
        .transformations(
            listOfNotNull(
                // 화면 크기로 Lanczos 리샘플링한 뒤 선명하게
                ImageEnhancer.lanczos(enhancement)?.let { LanczosTransformation(scaleType, it) },
                ImageEnhancer.sharpenAmount(enhancement)?.let { SharpenTransformation(it) },
            ),
        )
        .build()
}

/** 확대 상태에서 ▲▼ 한 번에 움직이는 양 — 넘치는 높이의 절반을 이만큼 나눈다 */
private const val PAN_STEPS = 2

/**
 * 스크롤 모드(너비 맞춤·원본)의 확대: 위쪽 가운데를 기준으로 키우고, 키운 높이만큼 자리를 차지해 끝까지 스크롤된다.
 * 좌우로 넘치는 부분은 잘린다.
 */
private fun Modifier.zoomedFromTop(zoom: Float): Modifier =
    if (zoom <= 1f) {
        this
    } else {
        clipToBounds().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, (placeable.height * zoom).roundToInt()) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
            }
        }
    }

/** Komelia의 크기 맞춤을 Compose ContentScale로 — 작은 쪽도 늘 맞춰 키운다 */
private fun contentScaleFor(scaleType: ScaleType): ContentScale = when (scaleType) {
    ScaleType.SCREEN -> ContentScale.Fit
    ScaleType.FIT_WIDTH -> ContentScale.FillWidth
    ScaleType.FIT_HEIGHT -> ContentScale.FillHeight
    ScaleType.ORIGINAL -> ContentScale.None
}

/** 진행 표시에 쓰는 쪽 — 펼침에서 보이는 큰 쪽, 세로 스크롤에서 보이는 큰 쪽 */
private val ReaderUiState.Reading.shownPage: Int
    get() = if (readerType == ReaderType.CONTINUOUS) currentContinuousPage else currentSpread.highestPageNumber
