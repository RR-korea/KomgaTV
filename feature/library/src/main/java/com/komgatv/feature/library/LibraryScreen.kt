package com.komgatv.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.komgatv.core.model.Series
import com.komgatv.core.ui.components.PosterCard
import com.komgatv.core.ui.util.KomgaUrls

/**
 * 라이브러리 화면
 *
 * 특정 라이브러리에 속한 시리즈를 그리드 형태로 표시합니다.
 * 표준 LazyVerticalGrid와 focusRestorer를 사용하여 D-Pad 네비게이션 및 포커스 복원을 지원하고,
 * 무한 스크롤(페이지 크기 50)로 대량의 시리즈를 효율적으로 로드합니다.
 *
 * @param libraryId 표시할 라이브러리 ID
 * @param onNavigateToSeries 시리즈 상세 화면으로 이동
 * @param onNavigateBack 뒤로가기
 * @param viewModel 라이브러리 화면 ViewModel
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryId: String,
    onNavigateToSeries: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    LaunchedEffect(libraryId) {
        viewModel.loadLibrary(libraryId)
    }

    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is LibraryUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.library_loading),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        is LibraryUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.library_error_prefix, state.message),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        is LibraryUiState.Success -> {
            LibraryContent(
                libraryName = state.libraryName,
                seriesList = state.series,
                isLoadingMore = state.isLoadingMore,
                isLastPage = state.isLastPage,
                baseUrl = viewModel.baseUrl,
                onNavigateToSeries = onNavigateToSeries,
                onLoadMore = { viewModel.loadMoreSeries() },
            )
        }
    }
}

/**
 * 라이브러리 콘텐츠 (표준 LazyVerticalGrid + focusRestorer로 포커스 복원 지원)
 */
@Composable
private fun LibraryContent(
    libraryName: String,
    seriesList: List<Series>,
    isLoadingMore: Boolean,
    isLastPage: Boolean,
    baseUrl: String,
    onNavigateToSeries: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = libraryName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = 48.dp,
                top = 32.dp,
                bottom = 16.dp,
            ),
        )

        // D4: 표준 LazyVerticalGrid + focusRestorer
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            state = gridState,
            contentPadding = PaddingValues(
                start = 48.dp,
                end = 48.dp,
                bottom = 48.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .focusRestorer(),
        ) {
            itemsIndexed(
                items = seriesList,
                key = { _, series -> series.id },
            ) { index, series ->
                // 마지막 아이템 근처 도달 시 다음 페이지 로드 트리거
                if (index >= seriesList.size - 5 && !isLastPage && !isLoadingMore) {
                    LaunchedEffect(index) {
                        onLoadMore()
                    }
                }

                PosterCard(
                    title = series.name,
                    imageUrl = KomgaUrls.seriesThumbnail(baseUrl, series.id),
                    subtitle = stringResource(R.string.series_books_count, series.booksCount),
                    onClick = { onNavigateToSeries(series.id) },
                )
            }

            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.library_loading_more),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
