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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.komgatv.core.ui.components.ProgressBar

/**
 * 전체 화면 만화 페이지 리더
 *
 * D-Pad 포커스 기반 onPreviewKeyEvent를 사용하여 리모컨 이벤트를 처리합니다:
 * - 좌/우: 이전/다음 페이지 (시리즈의 readingDirection이 RIGHT_TO_LEFT인 경우 반전)
 * - OK (DPAD_CENTER, ENTER): 오버레이 토글
 * - 위/아래 (오버레이 표시 시): 10페이지 점프 (위: +10, 아래: -10)
 * - Back: 진행 저장 후 종료
 *
 * 이웃 페이지(±2)는 화면 크기에 맞춰 백그라운드 프리로드됩니다.
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
                onDpadLeft = { viewModel.onDpadLeft() },
                onDpadRight = { viewModel.onDpadRight() },
                onToggleOverlay = { viewModel.toggleOverlay() },
                onJumpForward = { viewModel.jumpForward(10) },
                onJumpBackward = { viewModel.jumpBackward(10) },
                onNavigateBack = {
                    viewModel.saveProgressOnExit()
                    onNavigateBack()
                },
            )
        }
    }
}

/**
 * 리더 콘텐츠 컴포저블 (페이지 표시, 프리로드, 오버레이, 키 이벤트 처리)
 */
@Composable
private fun ReaderContent(
    state: ReaderUiState.Reading,
    focusRequester: FocusRequester,
    onDpadLeft: () -> Unit,
    onDpadRight: () -> Unit,
    onToggleOverlay: () -> Unit,
    onJumpForward: () -> Unit,
    onJumpBackward: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // 화면 크기 계산 (화면 크기 디코딩 및 OOM 방지)
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    // 현재 페이지 이미지 요청 (화면 크기 디코딩)
    val currentPageRequest = remember(state.currentPageUrl, screenWidthPx, screenHeightPx) {
        ImageRequest.Builder(context)
            .data(state.currentPageUrl)
            .size(screenWidthPx, screenHeightPx)
            .crossfade(false)
            .build()
    }

    // 이웃 페이지(±2) 백그라운드 프리로드
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

                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        onDpadLeft()
                        true
                    }
                    Key.DirectionRight -> {
                        onDpadRight()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        onToggleOverlay()
                        true
                    }
                    Key.DirectionUp -> {
                        if (state.showOverlay) {
                            onJumpForward()
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        if (state.showOverlay) {
                            onJumpBackward()
                            true
                        } else {
                            false
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
        AsyncImage(
            model = currentPageRequest,
            contentDescription = stringResource(
                R.string.reader_page_content_description,
                state.currentPage,
                state.totalPages,
            ),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = state.showOverlay,
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
                    Text(
                        text = state.bookTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.reader_page_progress_format,
                                state.currentPage,
                                state.totalPages,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        val percent = (state.currentPage.toFloat() / state.totalPages * 100).toInt()
                        Text(
                            text = stringResource(R.string.reader_page_percent_format, percent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ProgressBar(
                        progress = state.currentPage.toFloat() / state.totalPages.coerceAtLeast(1),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                    )
                }
            }
        }
    }
}
