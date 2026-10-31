package com.komgatv.feature.reader

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
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
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.komgatv.core.model.ReaderType
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import com.komgatv.core.ui.components.ProgressBar
import com.komgatv.core.ui.components.loadErrorText
import com.komgatv.feature.reader.continuous.ContinuousPages
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
    val listState = rememberLazyListState()

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

    // 이웃 펼침(±2) 프리로드 — 실제 표시와 같은 요청(크기·정밀도)이라야 메모리 캐시에 맞는다
    LaunchedEffect(state.preloadSpreads, state.scaleType, screenWidthPx, screenHeightPx) {
        val imageLoader = SingletonImageLoader.get(context)
        state.preloadSpreads.forEach { urls ->
            urls.forEach { url ->
                imageLoader.enqueue(
                    pageRequest(context, url, state.scaleType, slotWidth(screenWidthPx, urls.size), screenHeightPx)
                )
            }
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

    val contentScale = remember(state.scaleType, state.stretchToFit) {
        contentScaleFor(state.scaleType, state.stretchToFit)
    }

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
                    Key.DirectionCenter, Key.Enter -> {
                        if (state.showOverlay) viewModel.openSettingsPanel() else viewModel.toggleOverlay()
                    }
                    Key.DirectionUp -> if (state.showOverlay) {
                        viewModel.jumpPages(10)
                    } else if (continuous) {
                        coroutineScope.launch { listState.animateScrollBy(-screenHeightPx.toFloat()) }
                    } else if (scrollable && scrollState.value > 0) {
                        coroutineScope.launch {
                            scrollState.animateScrollTo((scrollState.value - screenHeightPx / 2).coerceAtLeast(0))
                        }
                    }
                    Key.DirectionDown -> if (state.showOverlay) {
                        viewModel.jumpPages(-10)
                    } else if (continuous) {
                        coroutineScope.launch { listState.animateScrollBy(screenHeightPx.toFloat()) }
                    } else if (scrollable && scrollState.value < scrollState.maxValue) {
                        coroutineScope.launch {
                            scrollState.animateScrollTo(
                                (scrollState.value + screenHeightPx / 2).coerceAtMost(scrollState.maxValue)
                            )
                        }
                    }
                    else -> return@onPreviewKeyEvent false
                }
                // 리더가 받는 방향키는 끝에 닿아도 소비한다 (포커스가 엉뚱한 곳으로 가지 않게)
                true
            },
    ) {
        val spreadContent: @Composable () -> Unit = {
            SpreadPages(
                state = state,
                contentScale = contentScale,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                scrollable = scrollable,
            )
        }

        if (continuous) {
            ContinuousPages(
                tiles = state.continuousTiles,
                listState = listState,
                scrollToTileIndex = state.scrollToTileIndex,
                loadTile = viewModel::loadTile,
                onScrolled = viewModel::onTileScrolled,
                onVisibleTilesChanged = viewModel::onContinuousVisibleTilesChanged,
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
                        .heightIn(min = screenHeightDp),
                    contentAlignment = Alignment.Center,
                ) {
                    spreadContent()
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                readerType = state.readerType,
                continuousPadding = state.continuousPadding,
                continuousPageSpacing = state.continuousPageSpacing,
                onReaderTypeChange = viewModel::setReaderType,
                onContinuousPaddingChange = viewModel::setContinuousPadding,
                onContinuousPageSpacingChange = viewModel::setContinuousPageSpacing,
                pageLayout = state.pageLayout,
                layoutOffset = state.layoutOffset,
                readingDirection = state.readingDirection,
                scaleType = state.scaleType,
                stretchToFit = state.stretchToFit,
                onPageLayoutChange = viewModel::setPageLayout,
                onLayoutOffsetChange = viewModel::setLayoutOffset,
                onReadingDirectionChange = viewModel::setReadingDirection,
                onScaleTypeChange = viewModel::setScaleType,
                onStretchToFitChange = viewModel::setStretchToFit,
            )
        }
    }
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
            val request = remember(url, state.scaleType, slotWidthPx, screenHeightPx) {
                pageRequest(context, url, state.scaleType, slotWidthPx, screenHeightPx)
            }
            AsyncImage(
                model = request,
                contentDescription = stringResource(
                    R.string.reader_page_content_description,
                    state.currentSpread.highestPageNumber,
                    state.totalPages,
                ),
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier
                    .weight(1f)
                    .then(if (scrollable) Modifier else Modifier.fillMaxHeight())
                    .then(pageModifier),
            )
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
        .build()
}

/** Komelia의 크기 맞춤 + 작은 이미지 키우기(stretchToFit)를 Compose ContentScale로 */
private fun contentScaleFor(scaleType: ScaleType, stretchToFit: Boolean): ContentScale {
    val base = when (scaleType) {
        ScaleType.SCREEN -> ContentScale.Fit
        ScaleType.FIT_WIDTH -> ContentScale.FillWidth
        ScaleType.FIT_HEIGHT -> ContentScale.FillHeight
        ScaleType.ORIGINAL -> return ContentScale.None
    }
    return if (stretchToFit) base else NoUpscale(base)
}

/** 줄이기만 하고 키우지는 않는 ContentScale */
private class NoUpscale(private val base: ContentScale) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        val factor = base.computeScaleFactor(srcSize, dstSize)
        return ScaleFactor(factor.scaleX.coerceAtMost(1f), factor.scaleY.coerceAtMost(1f))
    }
}

/** 진행 표시에 쓰는 쪽 — 펼침에서 보이는 큰 쪽, 세로 스크롤에서 보이는 큰 쪽 */
private val ReaderUiState.Reading.shownPage: Int
    get() = if (readerType == ReaderType.CONTINUOUS) currentContinuousPage else currentSpread.highestPageNumber
