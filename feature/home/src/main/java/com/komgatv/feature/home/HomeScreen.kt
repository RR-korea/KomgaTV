package com.komgatv.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Button
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.model.Book
import com.komgatv.core.model.Library
import com.komgatv.core.ui.components.PosterCard
import com.komgatv.core.ui.components.TVRow

/**
 * TV 홈 화면
 *
 * 앱의 메인 탐색 화면으로 다음 섹션을 포함합니다:
 * - "읽는 중" 행 (진행 중인 책 목록)
 * - "라이브러리" 행 (서버의 라이브러리 목록)
 *
 * @param onNavigateToLibrary 라이브러리 화면으로 이동
 * @param onNavigateToReader 리더 화면으로 이동
 * @param viewModel 홈 화면 ViewModel
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLibrary: (String) -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // 다른 화면(리더 등)에서 돌아올 때마다 조용히 다시 읽는다. 첫 진입은 ViewModel init이 읽는다.
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (entered) viewModel.refresh() else entered = true
    }

    // 마지막으로 연 항목 키. 돌아오면 이 항목에 포커스를 준다. 화면을 떠나도 남아야 해서 여기서 보관한다.
    // 포커스 변화로 기록하지 않는다: 새로고침으로 순서가 바뀌면 포커스가 옆 카드로 밀리면서 키가 덮인다.
    var openedKey by rememberSaveable { mutableStateOf<String?>(null) }

    when (val state = uiState) {
        is HomeUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.home_loading),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        is HomeUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.home_error_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    // 서버에 닿지 않을 때 리모컨만으로 설정을 고칠 수 있게 버튼에 포커스를 준다.
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    Button(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .focusRequester(focusRequester),
                    ) {
                        Text(stringResource(R.string.home_server_settings))
                    }
                }
            }
        }

        is HomeUiState.Success -> {
            HomeContent(
                continueReading = state.continueReading,
                libraries = state.libraries,
                imageUrls = viewModel.imageUrls,
                openedKey = openedKey,
                onItemOpened = { openedKey = it },
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToReader = onNavigateToReader,
                onNavigateToSettings = onNavigateToSettings,
            )
        }
    }
}

/**
 * 홈 화면 콘텐츠 (표준 LazyColumn + focusRestorer로 포커스 복원 지원)
 */
@Composable
private fun HomeContent(
    continueReading: List<Book>,
    libraries: List<Library>,
    imageUrls: ImageUrls,
    openedKey: String?,
    onItemOpened: (String) -> Unit,
    onNavigateToLibrary: (String) -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    // 들어올 때와 내용이 바뀔 때(돌아와서 새로고침하면 순서가 바뀐다) 포커스를 준다:
    // 마지막으로 연 항목(키로 찾으므로 자리가 바뀌어도 따라간다), 처음이거나 그 항목이 없어졌으면 첫 행.
    // 포커스 없이 시작하면 첫 키 입력이 이동 대신 포커스 잡기에 쓰인다.
    val itemFocus = remember { mutableMapOf<String, FocusRequester>() }
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(continueReading, libraries) {
        withFrameNanos { } // Lazy 항목이 배치된 뒤에 요청한다
        (openedKey?.let(itemFocus::get) ?: firstRowFocus).requestFocus()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer(firstRowFocus),
        contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
    ) {
        item(key = "home_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onNavigateToSettings) {
                    Text(stringResource(R.string.home_server_settings))
                }
            }
        }

        // ========== "읽는 중" 행 ==========
        if (continueReading.isNotEmpty()) {
            item(key = "home_continue_reading_row") {
                TVRow(
                    title = stringResource(R.string.home_continue_reading),
                    items = continueReading,
                    key = { it.id },
                    modifier = Modifier.focusRequester(firstRowFocus),
                ) { book ->
                    val progressSubtitle = if (book.readProgress?.completed == true) {
                        stringResource(R.string.home_book_completed)
                    } else {
                        stringResource(
                            R.string.home_book_progress,
                            book.readProgress?.page ?: 0,
                            book.pageCount,
                        )
                    }
                    val key = "book:${book.id}"
                    PosterCard(
                        modifier = Modifier.focusRequester(rememberItemFocus(key, itemFocus)),
                        // 책 이름이 "01"처럼 번호뿐이면 무슨 책인지 알 수 없어 시리즈 이름을 붙인다
                        title = if (book.name.any { it.isLetter() }) book.name else "${book.seriesTitle} ${book.name}",
                        imageUrl = imageUrls.bookThumbnail(book.id).orEmpty(),
                        subtitle = progressSubtitle,
                        onClick = {
                            onItemOpened(key)
                            onNavigateToReader(book.id)
                        },
                    )
                }
            }
        }

        // ========== "라이브러리" 행 ==========
        if (libraries.isNotEmpty()) {
            item(key = "home_libraries_row") {
                TVRow(
                    title = stringResource(R.string.home_libraries),
                    items = libraries,
                    key = { it.id },
                    // 읽는 중 행이 없으면 라이브러리 행이 첫 행이다
                    modifier = if (continueReading.isEmpty()) Modifier.focusRequester(firstRowFocus) else Modifier,
                ) { library ->
                    val key = "library:${library.id}"
                    PosterCard(
                        modifier = Modifier.focusRequester(rememberItemFocus(key, itemFocus)),
                        title = library.name,
                        imageUrl = "",
                        onClick = {
                            onItemOpened(key)
                            onNavigateToLibrary(library.id)
                        },
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

/**
 * 항목별 FocusRequester를 만들고, 화면에 있는 동안 [registry]에 등록해 둔다.
 */
@Composable
private fun rememberItemFocus(key: String, registry: MutableMap<String, FocusRequester>): FocusRequester {
    val requester = remember(key) { FocusRequester() }
    DisposableEffect(key) {
        registry[key] = requester
        onDispose { registry.remove(key) }
    }
    return requester
}
