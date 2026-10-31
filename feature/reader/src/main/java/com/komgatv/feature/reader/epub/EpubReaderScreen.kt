package com.komgatv.feature.reader.epub

import com.komgatv.core.model.EpubTocEntry
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.komgatv.core.ui.components.ProgressBar
import com.komgatv.core.ui.components.loadErrorText
import com.komgatv.feature.reader.R

/**
 * EPUB 리더
 *
 * 리모컨: 위/아래 = 화면 하나씩 스크롤 (장 끝에서 아래 = 다음 장, 장 처음에서 위 = 이전 장 끝),
 * 좌/우 = 이전/다음 장, OK = 오버레이(오버레이에서 위/아래 = 글자 크기), Back = 오버레이 닫기 → 나가기.
 * 글은 어두운 배경에 밝은 글자로, 가운데 읽기 좋은 폭으로 보인다.
 */
@Composable
fun EpubReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    viewModel: EpubViewModel = hiltViewModel(),
) {
    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }
    DisposableEffect(Unit) { onDispose { viewModel.saveOnExit() } }

    when (val state = viewModel.uiState.collectAsState().value) {
        EpubUiState.Loading -> Message(stringResource(R.string.reader_loading_page), Color.White)
        is EpubUiState.Error -> Message(stringResource(R.string.reader_error_prefix, loadErrorText(state.error)), Color.Red)
        is EpubUiState.Reading -> EpubContent(state, viewModel, onNavigateBack)
    }
}

@Composable
private fun Message(text: String, color: Color) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(text = text, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpubContent(
    state: EpubUiState.Reading,
    viewModel: EpubViewModel,
    onNavigateBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val currentState by rememberUpdatedState(state)

    BackHandler {
        if (state.showToc) {
            viewModel.closeToc()
        } else if (state.showOverlay) {
            viewModel.closeOverlay()
        } else {
            viewModel.saveOnExit()
            onNavigateBack()
        }
    }

    // 처음과 목차를 닫았을 때 리더가 키를 받는다
    LaunchedEffect(state.showToc) { if (!state.showToc) focusRequester.requestFocus() }

    // 장이 바뀌면 그 장을 연다 (시작 위치는 다 열린 뒤 맞춘다)
    LaunchedEffect(webView, state.chapter.href) {
        webView?.loadUrl(state.chapter.href)
    }
    LaunchedEffect(webView, state.textZoom) {
        webView?.settings?.textZoom = state.textZoom
    }
    // 글자 크기를 바꾼 뒤처럼, 장은 그대로인데 시작 위치가 새로 정해지면 다시 맞춘다
    LaunchedEffect(webView, state.startProgression, state.textZoom) {
        val view = webView ?: return@LaunchedEffect
        val start = state.startProgression ?: return@LaunchedEffect
        if (view.progress == 100) {
            view.evaluateJavascript(scrollToScript(start)) { viewModel.onChapterShown() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(BACKGROUND))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // 목차가 열려 있으면 키는 목차 줄이 받는다
                if (state.showToc) return@onPreviewKeyEvent false
                val view = webView ?: return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> if (state.showOverlay) {
                        viewModel.changeTextZoom(-1)
                    } else {
                        view.evaluateJavascript(SCROLL_DOWN) { result ->
                            if (result == "\"end\"") viewModel.nextChapter() else result.toProgression()?.let(viewModel::onScrolled)
                        }
                    }
                    Key.DirectionUp -> if (state.showOverlay) {
                        viewModel.changeTextZoom(+1)
                    } else {
                        view.evaluateJavascript(SCROLL_UP) { result ->
                            if (result == "\"start\"") {
                                viewModel.previousChapter(fromTop = false)
                            } else {
                                result.toProgression()?.let(viewModel::onScrolled)
                            }
                        }
                    }
                    Key.DirectionLeft -> viewModel.previousChapter()
                    Key.DirectionRight -> viewModel.nextChapter()
                    Key.DirectionCenter, Key.Enter -> if (state.showOverlay) viewModel.openToc() else viewModel.toggleOverlay()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    // 키는 위의 Box가 받는다 — WebView가 포커스를 가져가지 않게
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setBackgroundColor(BACKGROUND.toInt())
                    settings.javaScriptEnabled = true
                    settings.textZoom = state.textZoom
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val resource = viewModel.loadResource(request.url.toString()) ?: return null
                            return WebResourceResponse(
                                resource.mimeType,
                                resource.encoding,
                                resource.statusCode,
                                if (resource.statusCode in 200..299) "OK" else "Error",
                                emptyMap(),
                                resource.body,
                            )
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript(STYLE_SCRIPT, null)
                            val start = currentState.startProgression ?: return
                            view.evaluateJavascript(scrollToScript(start)) { viewModel.onChapterShown() }
                        }
                    }
                }.also { webView = it }
            },
            onRelease = { it.destroy() },
        )

        if (state.showOverlay && !state.showToc) EpubOverlay(state)
        if (state.showToc) EpubToc(state, onSelect = viewModel::jumpToToc)
    }
}

@Composable
private fun EpubOverlay(state: EpubUiState.Reading) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            Text(text = state.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            val chapterTitle = state.chapter.title
            Text(
                text = if (chapterTitle.isNullOrBlank()) {
                    stringResource(R.string.epub_chapter_number_format, state.chapterIndex + 1, state.chapters.size)
                } else {
                    stringResource(R.string.epub_chapter_format, state.chapterIndex + 1, state.chapters.size, chapterTitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            ProgressBar(progress = state.totalProgression, modifier = Modifier.fillMaxWidth().height(4.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.epub_overlay_hint, state.textZoom),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

private const val BACKGROUND = 0xFF121212

/** JS 결과("0.42" 같은 문자열)를 숫자로 */
private fun String.toProgression(): Float? = trim('"').toFloatOrNull()

/** 어두운 배경, 밝은 글자, 읽기 좋은 폭 — 책 CSS보다 우선 */
private val STYLE_SCRIPT = """
    (function(){
      var s=document.createElement('style');
      s.textContent='html,body{background:#121212!important;color:#e6e6e6!important}'+
        'body{max-width:46em!important;margin:0 auto!important;padding:5vh 4vw!important;line-height:1.8!important}'+
        'img,svg,video{max-width:100%!important;height:auto!important}a{color:#80b9ff!important}'+
        '*{background-color:transparent!important}';
      document.head.appendChild(s);
    })();
""".trimIndent()

private fun scrollToScript(progression: Float) = """
    (function(){var h=document.documentElement.scrollHeight-innerHeight;window.scrollTo(0,Math.max(0,h*$progression));})();
""".trimIndent()

/** 화면 90%만큼 아래로. 이미 끝이면 "end", 아니면 장 안 위치 */
private val SCROLL_DOWN = """
    (function(){var h=document.documentElement.scrollHeight-innerHeight;
      if(scrollY>=h-2)return 'end';window.scrollBy(0,innerHeight*0.9);return String(h<=0?1:scrollY/h);})();
""".trimIndent()

/** 화면 90%만큼 위로. 이미 처음이면 "start", 아니면 장 안 위치 */
private val SCROLL_UP = """
    (function(){var h=document.documentElement.scrollHeight-innerHeight;
      if(scrollY<=2)return 'start';window.scrollBy(0,-innerHeight*0.9);return String(h<=0?0:scrollY/h);})();
""".trimIndent()

/**
 * 목차 패널 — 지금 장의 줄에 포커스, 위/아래로 고르고 OK로 그 장 처음으로. Back은 오버레이로.
 */
@Composable
private fun EpubToc(state: EpubUiState.Reading, onSelect: (EpubTocEntry) -> Unit) {
    val current = remember(state.toc, state.chapterIndex) {
        currentTocIndex(state.toc, state.chapters, state.chapterIndex)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { currentFocus.requestFocus() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(520.dp)
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(stringResource(R.string.epub_toc_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(12.dp))
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(state.toc) { index, entry ->
                    TocRow(
                        title = entry.title.ifBlank { stringResource(R.string.epub_chapter_short, index + 1) },
                        depth = entry.depth,
                        isCurrent = index == current,
                        onClick = { onSelect(entry) },
                        modifier = if (index == current) Modifier.focusRequester(currentFocus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun TocRow(title: String, depth: Int, isCurrent: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isCurrent) Color(0xFF80B9FF) else Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color(0xFF3A3A3A) else Color.Transparent)
            .border(2.dp, if (focused) Color(0xFF80B9FF) else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = (12 + depth * 20).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    )
}
