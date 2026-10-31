package com.komgatv.feature.reader

import android.view.KeyEvent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LinearProgressIndicator
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * 전체 화면 만화 페이지 리더
 *
 * D-Pad 좌/우로 이전/다음 페이지를 탐색하고,
 * 중앙/선택 버튼으로 페이지 번호와 진행률 오버레이를 토글합니다.
 * 3-페이지 규칙(이전/현재/다음)에 따라 메모리에 유지합니다.
 *
 * @param bookId 읽을 책 ID
 * @param onNavigateBack 뒤로가기 콜백 (진행 상태 저장 후 호출)
 * @param onRegisterKeyEventHandler 액티비티 레벨 키 핸들러 등록
 * @param viewModel 리더 ViewModel
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    onRegisterKeyEventHandler: ((KeyEvent) -> Boolean) -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    // 책 데이터 로드
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    // 포커스 요청 (키 이벤트 수신을 위해)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 화면 종료 시 진행 상태 저장
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
                    text = "페이지 로딩 중...",
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
                    text = "오류: ${state.message}",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        is ReaderUiState.Reading -> {
            ReaderContent(
                state = state,
                focusRequester = focusRequester,
                onNextPage = { viewModel.nextPage() },
                onPreviousPage = { viewModel.previousPage() },
                onToggleOverlay = { viewModel.toggleOverlay() },
                onNavigateBack = {
                    viewModel.saveProgressOnExit()
                    onNavigateBack()
                },
            )
        }
    }
}

/**
 * 리더 콘텐츠 (실제 페이지 표시)
 *
 * D-Pad 키 이벤트를 처리하여 페이지 탐색과 오버레이 토글을 수행합니다.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ReaderContent(
    state: ReaderUiState.Reading,
    focusRequester: FocusRequester,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onToggleOverlay: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current

    // 현재 페이지 이미지 요청 (remember로 불필요한 재생성 방지)
    val currentPageRequest = remember(state.currentPageUrl) {
        ImageRequest.Builder(context)
            .data(state.currentPageUrl)
            .crossfade(false)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    return@onKeyEvent false
                }

                when (keyEvent.nativeKeyEvent.keyCode) {
                    // D-Pad 우측: 다음 페이지
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onNextPage()
                        true
                    }
                    // D-Pad 좌측: 이전 페이지
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onPreviousPage()
                        true
                    }
                    // 중앙/선택 버튼: 오버레이 토글
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        onToggleOverlay()
                        true
                    }
                    // 뒤로가기: 리더 종료 (진행 상태 저장)
                    KeyEvent.KEYCODE_BACK -> {
                        onNavigateBack()
                        true
                    }
                    else -> false
                }
            },
    ) {
        // ========== 현재 페이지 이미지 ==========
        AsyncImage(
            model = currentPageRequest,
            contentDescription = "${state.currentPage}/${state.totalPages} 페이지",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // ========== 다음 페이지 프리로드 (보이지 않는 영역) ==========
        // 3-페이지 규칙: 다음 페이지만 미리 로드
        if (state.nextPageUrl != null) {
            val nextPageRequest = remember(state.nextPageUrl) {
                ImageRequest.Builder(context)
                    .data(state.nextPageUrl)
                    .crossfade(false)
                    .size(1) // 최소 크기로 요청하여 캐시에만 저장
                    .build()
            }
            // Coil의 캐시에 미리 로드
            AsyncImage(
                model = nextPageRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0f), // 보이지 않게 처리
            )
        }

        // ========== 정보 오버레이 ==========
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
                    // 책 제목
                    Text(
                        text = state.bookTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 페이지 번호 표시
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${state.currentPage} / ${state.totalPages}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        Text(
                            text = "${(state.currentPage.toFloat() / state.totalPages * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 진행률 바
                    LinearProgressIndicator(
                        progress = {
                            state.currentPage.toFloat() / state.totalPages.coerceAtLeast(1)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                    )
                }
            }
        }
    }
}
