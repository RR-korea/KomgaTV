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
import com.komgatv.core.ui.components.NameCard
import com.komgatv.core.ui.components.PosterCard
import com.komgatv.core.ui.components.TVRow
import com.komgatv.core.ui.components.loadErrorText
import com.komgatv.core.ui.focus.EntryFocusEffect
import com.komgatv.core.ui.focus.EntryFocusState
import com.komgatv.core.ui.focus.OnReturnEffect
import com.komgatv.core.ui.focus.rememberEntryFocusState
import com.komgatv.core.ui.focus.requesterFor

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

    // 다른 화면(리더 등)에서 돌아올 때마다 조용히 다시 읽는다(진행도·순서). 첫 진입은 ViewModel init이 읽는다.
    OnReturnEffect { viewModel.refresh() }
    val entryFocus = rememberEntryFocusState()

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
                        text = loadErrorText(state.error),
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
                entryFocus = entryFocus,
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
    entryFocus: EntryFocusState,
    onNavigateToLibrary: (String) -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    // 새로고침하면 읽는 중 순서가 바뀌므로 내용이 바뀔 때도 연 항목으로 포커스를 다시 준다.
    // 처음이거나 그 항목이 없어졌으면 첫 행.
    EntryFocusEffect(entryFocus, continueReading, libraries)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer(entryFocus.fallback),
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
                    modifier = Modifier.focusRequester(entryFocus.fallback),
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
                        modifier = Modifier.focusRequester(entryFocus.requesterFor(key)),
                        // 책 이름이 "01"처럼 번호뿐이면 무슨 책인지 알 수 없어 시리즈 이름을 붙인다
                        title = if (book.name.any { it.isLetter() }) book.name else "${book.seriesTitle} ${book.name}",
                        imageUrl = imageUrls.bookThumbnail(book.id).orEmpty(),
                        subtitle = progressSubtitle,
                        onClick = {
                            entryFocus.onOpen(key)
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
                    modifier = if (continueReading.isEmpty()) Modifier.focusRequester(entryFocus.fallback) else Modifier,
                ) { library ->
                    val key = "library:${library.id}"
                    // Komga 라이브러리에는 표지가 없다 — 이름 카드로
                    NameCard(
                        modifier = Modifier.focusRequester(entryFocus.requesterFor(key)),
                        title = library.name,
                        onClick = {
                            entryFocus.onOpen(key)
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
