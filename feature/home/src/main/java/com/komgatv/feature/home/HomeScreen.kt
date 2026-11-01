package com.komgatv.feature.home

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.model.Book
import com.komgatv.core.model.Library
import com.komgatv.core.model.Series
import com.komgatv.core.model.cardTitle
import com.komgatv.core.model.cardTitleShowsSeries
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
    onNavigateToSeries: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val libraryMessage by viewModel.libraryMessage.collectAsState()
    var menuLibrary by remember { mutableStateOf<Library?>(null) }
    var menuReadingBook by remember { mutableStateOf<Book?>(null) }

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
                state = state,
                imageUrls = viewModel.imageUrls,
                entryFocus = entryFocus,
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToReader = onNavigateToReader,
                onNavigateToSeries = onNavigateToSeries,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSearch = onNavigateToSearch,
                onLibraryMenu = { menuLibrary = it },
                onReadingMenu = { menuReadingBook = it },
            )
        }
    }

    menuLibrary?.let { library ->
        LibraryActionsDialog(
            library = library,
            onScan = { viewModel.scanLibrary(library.id) },
            onEmptyTrash = { viewModel.emptyTrash(library.id) },
            onDismiss = { menuLibrary = null },
        )
    }
    menuReadingBook?.let { book ->
        ReadingActionsDialog(
            book = book,
            onHide = { viewModel.hideFromReading(book) },
            onDismiss = { menuReadingBook = null },
        )
    }
    libraryMessage?.let { LibraryActionMessage(it, onShown = viewModel::clearLibraryMessage) }
}

/** 홈의 한 줄: 제목, 항목, 항목을 그리는 법 */
private class HomeRow<T>(
    val key: String,
    val titleRes: Int,
    val items: List<T>,
    val itemKey: (T) -> String,
    val content: @Composable (T, Modifier) -> Unit,
)

/**
 * 홈 화면 콘텐츠 (표준 LazyColumn + focusRestorer로 포커스 복원 지원)
 *
 * 줄 순서는 Komelia 홈과 같다: 읽는 중, 다음 권, 최근 추가된 책, 최근 추가된 시리즈, 최근 업데이트된 시리즈, 라이브러리.
 * 빈 줄은 그리지 않는다.
 */
@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    imageUrls: ImageUrls,
    entryFocus: EntryFocusState,
    onNavigateToLibrary: (String) -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToSeries: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onLibraryMenu: (Library) -> Unit,
    onReadingMenu: (Book) -> Unit,
) {
    // 새로고침하면 읽는 중·다음 권 순서가 바뀌므로 내용이 바뀔 때도 연 항목으로 포커스를 다시 준다.
    // 처음이거나 그 항목이 없어졌으면 첫 줄.
    EntryFocusEffect(entryFocus, state.continueReading, state.onDeck, state.libraries)

    @Composable
    fun bookCard(
        rowKey: String,
        book: Book,
        modifier: Modifier,
        showProgress: Boolean,
        onLongClick: (() -> Unit)? = null,
    ) {
        val key = "$rowKey:${book.id}"
        PosterCard(
            modifier = modifier.focusRequester(entryFocus.requesterFor(key)),
            // 번호뿐인 이름엔 시리즈 이름을 붙이고, 해시 이름이면 시리즈 이름을 쓴다
            title = book.cardTitle(),
            imageUrl = imageUrls.bookThumbnail(book.id).orEmpty(),
            subtitle = when {
                !showProgress -> book.seriesTitle.takeUnless { book.cardTitleShowsSeries() }
                book.readProgress?.completed == true -> stringResource(R.string.home_book_completed)
                else -> stringResource(R.string.home_book_progress, book.readProgress?.page ?: 0, book.pageCount)
            },
            onClick = {
                entryFocus.onOpen(key)
                onNavigateToReader(book.id)
            },
            onLongClick = onLongClick,
        )
    }

    @Composable
    fun seriesCard(rowKey: String, series: Series, modifier: Modifier) {
        val key = "$rowKey:${series.id}"
        PosterCard(
            modifier = modifier.focusRequester(entryFocus.requesterFor(key)),
            title = series.name,
            imageUrl = imageUrls.seriesThumbnail(series.id).orEmpty(),
            subtitle = pluralStringResource(R.plurals.home_series_books_count, series.booksCount, series.booksCount),
            onClick = {
                entryFocus.onOpen(key)
                onNavigateToSeries(series.id)
            },
        )
    }

    val rows = listOf(
        HomeRow("continue", R.string.home_continue_reading, state.continueReading, { it.id }) { book, m ->
            // 길게 누르면 "읽는 중에서 빼기"
            bookCard("continue", book, m, showProgress = true, onLongClick = { onReadingMenu(book) })
        },
        HomeRow("ondeck", R.string.home_on_deck, state.onDeck, { it.id }) { book, m ->
            bookCard("ondeck", book, m, showProgress = false, onLongClick = { onReadingMenu(book) })
        },
        HomeRow("latest", R.string.home_latest_books, state.latestBooks, { it.id }) { book, m ->
            bookCard("latest", book, m, showProgress = false)
        },
        HomeRow("newseries", R.string.home_new_series, state.newSeries, { it.id }) { series, m ->
            seriesCard("newseries", series, m)
        },
        HomeRow("updatedseries", R.string.home_updated_series, state.updatedSeries, { it.id }) { series, m ->
            seriesCard("updatedseries", series, m)
        },
        HomeRow("library", R.string.home_libraries, state.libraries, { it.id }) { library, m ->
            val key = "library:${library.id}"
            // Komga 라이브러리에는 표지가 없다 — 이름 카드로
            NameCard(
                modifier = m.focusRequester(entryFocus.requesterFor(key)),
                title = library.name,
                onClick = {
                    entryFocus.onOpen(key)
                    onNavigateToLibrary(library.id)
                },
                // OK를 길게 — 라이브러리 메뉴 (스캔, 휴지통 비우기)
                onLongClick = { onLibraryMenu(library) },
            )
        },
    ).filter { it.items.isNotEmpty() }

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
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onNavigateToSearch) {
                    Text(stringResource(R.string.home_search))
                }
                OutlinedButton(onClick = onNavigateToSettings) {
                    Text(stringResource(R.string.home_server_settings))
                }
            }
        }

        rows.forEachIndexed { index, row ->
            item(key = "home_row_${row.key}") {
                @Suppress("UNCHECKED_CAST")
                val typed = row as HomeRow<Any>
                TVRow(
                    title = stringResource(typed.titleRes),
                    items = typed.items,
                    key = typed.itemKey,
                    // 처음 들어오면 맨 위 줄에 포커스
                    modifier = if (index == 0) Modifier.focusRequester(entryFocus.fallback) else Modifier,
                ) { item ->
                    typed.content(item, Modifier)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
