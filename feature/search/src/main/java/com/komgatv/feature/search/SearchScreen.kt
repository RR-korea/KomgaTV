package com.komgatv.feature.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.ui.components.PosterCard
import com.komgatv.core.ui.components.TvTextField
import com.komgatv.core.ui.components.loadErrorText
import com.komgatv.core.ui.focus.EntryFocusEffect
import com.komgatv.core.ui.focus.EntryFocusState
import com.komgatv.core.ui.focus.rememberEntryFocusState
import com.komgatv.core.ui.focus.requesterFor

/**
 * 시리즈 검색 화면
 *
 * 처음엔 입력칸에 포커스(OK로 키보드), 결과를 열고 돌아오면 그 결과에 포커스.
 * 음성 인식 앱이 있으면 "음성" 버튼으로 말해서 찾는다 (TV 키보드의 마이크도 쓸 수 있다).
 */
@Composable
fun SearchScreen(
    onNavigateToSeries: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val entryFocus = rememberEntryFocusState()
    EntryFocusEffect(entryFocus)
    // 입력칸에서 아래 = 첫 결과 (가장 가까운 카드가 아니라)
    val firstResult = remember { FocusRequester() }
    val hasResults = uiState is SearchUiState.Results

    val context = LocalContext.current
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }
    val voiceAvailable = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::onQueryChange)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 48.dp, top = 32.dp, bottom = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = stringResource(R.string.search_placeholder),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(entryFocus.fallback)
                    .focusProperties { if (hasResults) down = firstResult },
            )
            if (voiceAvailable) {
                OutlinedButton(onClick = { voiceLauncher.launch(voiceIntent) }) {
                    Text(stringResource(R.string.search_voice))
                }
            }
        }

        when (val state = uiState) {
            SearchUiState.Idle -> Message(stringResource(R.string.search_hint))
            SearchUiState.Loading -> Message(stringResource(R.string.search_loading))
            is SearchUiState.Empty -> Message(stringResource(R.string.search_empty, state.query))
            is SearchUiState.Error -> Message(loadErrorText(state.error))
            is SearchUiState.Results -> ResultsGrid(
                state = state,
                imageUrls = viewModel.imageUrls,
                entryFocus = entryFocus,
                firstResult = firstResult,
                onNavigateToSeries = onNavigateToSeries,
                onLoadMore = viewModel::loadMore,
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.TopStart) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultsGrid(
    state: SearchUiState.Results,
    imageUrls: ImageUrls,
    entryFocus: EntryFocusState,
    firstResult: FocusRequester,
    onNavigateToSeries: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        // 위 여백: 포커스로 커진 첫 줄 카드의 테두리가 잘리지 않게
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer(),
    ) {
        itemsIndexed(state.series, key = { _, series -> series.id }) { index, series ->
            if (index >= state.series.size - 5 && !state.isLastPage && !state.isLoadingMore) {
                LaunchedEffect(index) { onLoadMore() }
            }
            val key = "series:${series.id}"
            PosterCard(
                modifier = Modifier
                    .focusRequester(entryFocus.requesterFor(key))
                    .then(if (index == 0) Modifier.focusRequester(firstResult) else Modifier),
                title = series.name,
                imageUrl = imageUrls.seriesThumbnail(series.id).orEmpty(),
                subtitle = pluralStringResource(R.plurals.search_books_count, series.booksCount, series.booksCount),
                onClick = {
                    entryFocus.onOpen(key)
                    onNavigateToSeries(series.id)
                },
            )
        }
    }
}
