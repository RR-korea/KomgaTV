package com.komgatv.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import com.komgatv.core.ui.components.ProgressBar
import kotlinx.coroutines.launch

/**
 * 전체 화면 만화 페이지 리더
 *
 * D-Pad 포커스 기반 onPreviewKeyEvent를 사용하여 리모컨 이벤트를 처리합니다:
 * - 좌/우: 이전/다음 펼침 (readingDirection이 RIGHT_TO_LEFT인 경우 반전)
 * - OK (DPAD_CENTER, ENTER): 오버레이 토글
 * - 위/아래:
 *   - 오버레이 표시 시: 10페이지 점프 (위: +10, 아래: -10)
 *   - 오버레이 닫힌 상태에서 화면보다 큰 경우: 화면 절반씩 수직 스크롤 (끝에 닿으면 무반응)
 * - Back:
 *   - 보기 설정 패널 열림 시: 패널 닫기
 *   - 그 외: 진행 저장 후 종료
 *
 * 이웃 펼침(±2)은 화면 크기에 맞춰 백그라운드 프리로드됩니다.
 *
 * @param bookId 읽을 책 ID
 * @param onNavigateBack 뒤로가기 콜백
 * @param viewModel 리더 ViewModel
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveProgressOnExit()
        }
    }

    when (val state = uiState) {
        is ReaderUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.reader_loading_page),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        is ReaderUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.reader_error_prefix, state.message),
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        is ReaderUiState.Reading -> {
            ReaderContent(
                state = state,
                focusRequester = focusRequester,
                viewModel = viewModel,
                onNavigateBack = {
                    viewModel.saveProgressOnExit()
                    onNavigateBack()
                },
            )
        }
    }
}

/**
 * 리더 콘텐츠 컴포저블 (펼침 표시, 프리로드, 스크롤, 오버레이, 설정 패널, 키 이벤트 처리)
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ReaderContent(
    state: ReaderUiState.Reading,
    focusRequester: FocusRequester,
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidthPx = containerSize.width
    val screenHeightPx = containerSize.height

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // 펼침이 변경되면 스크롤 위치 초기화
    LaunchedEffect(state.currentSpreadIndex) {
        scrollState.scrollTo(0)
    }

    // 이웃 펼침(±2) 백그라운드 프리로드
    LaunchedEffect(state.preloadUrls, screenWidthPx, screenHeightPx) {
        val imageLoader = SingletonImageLoader.get(context)
        state.preloadUrls.forEach { preloadUrl ->
            val preloadRequest = ImageRequest.Builder(context)
                .data(preloadUrl)
                .size(screenWidthPx, screenHeightPx)
                .crossfade(false)
                .build()
            imageLoader.enqueue(preloadRequest)
        }
    }

    val contentScale = when (state.scaleType) {
        ScaleType.SCREEN -> if (state.stretchToFit) ContentScale.Fit else ContentScale.Inside
        ScaleType.FIT_WIDTH -> ContentScale.FillWidth
        ScaleType.FIT_HEIGHT -> ContentScale.FillHeight
        ScaleType.ORIGINAL -> ContentScale.None
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                if (state.showSettingsPanel) {
                    if (keyEvent.key == Key.Back) {
                        viewModel.closeSettingsPanel()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        viewModel.onDpadLeft()
                        true
                    }
                    Key.DirectionRight -> {
                        viewModel.onDpadRight()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        viewModel.toggleOverlay()
                        true
                    }
                    Key.DirectionUp -> {
                        if (state.showOverlay) {
                            viewModel.jumpForward(10)
                            true
                        } else {
                            if (scrollState.value > 0) {
                                coroutineScope.launch {
                                    val target = (scrollState.value - screenHeightPx / 2).coerceAtLeast(0)
                                    scrollState.animateScrollTo(target)
                                }
                                true
                            } else {
                                false
                            }
                        }
                    }
                    Key.DirectionDown -> {
                        if (state.showOverlay) {
                            viewModel.jumpBackward(10)
                            true
                        } else {
                            if (scrollState.value < scrollState.maxValue) {
                                coroutineScope.launch {
                                    val target = (scrollState.value + screenHeightPx / 2).coerceAtMost(scrollState.maxValue)
                                    scrollState.animateScrollTo(target)
                                }
                                true
                            } else {
                                false
                            }
                        }
                    }
                    Key.Back -> {
                        onNavigateBack()
                        true
                    }
                    else -> false
                }
            },
    ) {
        // 본문 이미지 렌더링 영역 (화면보다 큰 경우 수직 스크롤 지원)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            contentAlignment = Alignment.Center,
        ) {
            if (state.spreadPageUrls.size == 1) {
                val url = state.spreadPageUrls.first()
                val request = remember(url, screenWidthPx, screenHeightPx) {
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(screenWidthPx, screenHeightPx)
                        .crossfade(false)
                        .build()
                }

                AsyncImage(
                    model = request,
                    contentDescription = stringResource(
                        R.string.reader_page_content_description,
                        state.currentSpread.highestPageNumber,
                        state.totalPages,
                    ),
                    contentScale = contentScale,
                    modifier = if (state.scaleType == ScaleType.FIT_WIDTH) {
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    } else {
                        Modifier.fillMaxSize()
                    },
                )
            } else if (state.spreadPageUrls.size >= 2) {
                // RTL이면 첫 쪽(0번)이 오른쪽, 다음 쪽(1번)이 왼쪽
                val displayUrls = if (state.readingDirection == ReadingDirection.RIGHT_TO_LEFT) {
                    listOf(state.spreadPageUrls[1], state.spreadPageUrls[0])
                } else {
                    listOf(state.spreadPageUrls[0], state.spreadPageUrls[1])
                }

                Row(
                    modifier = if (state.scaleType == ScaleType.FIT_WIDTH) {
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    } else {
                        Modifier.fillMaxSize()
                    },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    displayUrls.forEach { url ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (state.scaleType == ScaleType.FIT_WIDTH) {
                                        Modifier.wrapContentHeight()
                                    } else {
                                        Modifier.fillMaxHeight()
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val request = remember(url, screenWidthPx, screenHeightPx) {
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .size(screenWidthPx / 2, screenHeightPx)
                                    .crossfade(false)
                                    .build()
                            }

                            AsyncImage(
                                model = request,
                                contentDescription = null,
                                contentScale = contentScale,
                                modifier = if (state.scaleType == ScaleType.FIT_WIDTH) {
                                    Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                } else {
                                    Modifier.fillMaxSize()
                                },
                            )
                        }
                    }
                }
            }
        }

        // 정보 오버레이
        AnimatedVisibility(
            visible = state.showOverlay && !state.showSettingsPanel,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.bookTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )

                        Button(
                            onClick = { viewModel.openSettingsPanel() },
                        ) {
                            Text(text = stringResource(R.string.reader_settings_button))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val pageNums = state.currentSpread.pages.map { it.number }.joinToString(", ")
                        Text(
                            text = stringResource(
                                R.string.reader_spread_progress_format,
                                state.currentSpreadIndex + 1,
                                state.totalSpreads,
                                pageNums,
                                state.totalPages,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        val percent = (state.currentSpread.highestPageNumber.toFloat() / state.totalPages * 100).toInt()
                        Text(
                            text = stringResource(R.string.reader_page_percent_format, percent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ProgressBar(
                        progress = state.currentSpread.highestPageNumber.toFloat() / state.totalPages.coerceAtLeast(1),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                    )
                }
            }
        }

        // 보기 설정 패널
        AnimatedVisibility(
            visible = state.showSettingsPanel,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ReaderSettingsPanel(
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
                onClose = viewModel::closeSettingsPanel,
            )
        }
    }
}
