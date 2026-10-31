package com.komgatv.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.komgatv.core.model.Book
import com.komgatv.core.model.Series
import com.komgatv.core.ui.components.PosterCard
import com.komgatv.core.ui.util.KomgaUrls

/**
 * 시리즈 상세 화면
 *
 * 시리즈의 상세 정보(제목, 요약, 장르, 출판사 등)와 시리즈에 속한 책 목록 및 읽기 진행률을 표시합니다.
 * 표준 LazyVerticalGrid와 focusRestorer를 사용하여 D-Pad 조작 및 화면 복귀 시 포커스 복원을 지원합니다.
 *
 * @param seriesId 시리즈 ID
 * @param onNavigateToReader 책 선택 시 리더 화면으로 이동
 * @param onNavigateBack 뒤로가기
 * @param viewModel 시리즈 상세 화면 ViewModel
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: String,
    onNavigateToReader: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) {
        viewModel.loadSeriesDetail(seriesId)
    }

    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is SeriesDetailUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.series_detail_loading),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        is SeriesDetailUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.series_detail_error_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        is SeriesDetailUiState.Success -> {
            SeriesDetailContent(
                series = state.series,
                books = state.books,
                isLoadingMore = state.isLoadingMore,
                isLastPage = state.isLastPage,
                baseUrl = viewModel.baseUrl,
                onNavigateToReader = onNavigateToReader,
                onLoadMore = { viewModel.loadMoreBooks() },
            )
        }
    }
}

/**
 * 시리즈 상세 화면 콘텐츠
 */
@Composable
private fun SeriesDetailContent(
    series: Series,
    books: List<Book>,
    isLoadingMore: Boolean,
    isLastPage: Boolean,
    baseUrl: String,
    onNavigateToReader: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        contentPadding = PaddingValues(
            start = 48.dp,
            end = 48.dp,
            top = 32.dp,
            bottom = 48.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer(),
    ) {
        // ========== 상단: 시리즈 메타데이터 헤더 ==========
        item(
            key = "series_header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            SeriesHeader(series = series, baseUrl = baseUrl)
        }

        // ========== 섹션 제목: 도서 목록 ==========
        item(
            key = "books_header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    text = stringResource(R.string.series_detail_books_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // ========== 도서 목록 그리드 ==========
        itemsIndexed(
            items = books,
            key = { _, book -> book.id },
        ) { index, book ->
            if (index >= books.size - 5 && !isLastPage && !isLoadingMore) {
                LaunchedEffect(index) {
                    onLoadMore()
                }
            }

            val progress = book.readProgress
            val progressText = when {
                progress?.completed == true -> {
                    stringResource(R.string.series_detail_completed)
                }
                progress != null -> {
                    stringResource(
                        R.string.series_detail_progress,
                        progress.page,
                        book.pageCount,
                    )
                }
                else -> {
                    stringResource(R.string.series_detail_unread)
                }
            }

            PosterCard(
                title = book.name,
                imageUrl = KomgaUrls.bookThumbnail(baseUrl, book.id),
                subtitle = progressText,
                onClick = { onNavigateToReader(book.id) },
            )
        }

        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
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

/**
 * 시리즈 메타데이터 헤더
 */
@Composable
private fun SeriesHeader(
    series: Series,
    baseUrl: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        // 시리즈 대표 포스터 이미지
        AsyncImage(
            model = KomgaUrls.seriesThumbnail(baseUrl, series.id),
            contentDescription = series.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp)),
        )

        Spacer(modifier = Modifier.width(32.dp))

        // 메타데이터 텍스트 정보
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = series.name,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 권 수 및 상태
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.series_detail_books_count, series.booksCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (series.metadata.status.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.series_detail_status_prefix, series.metadata.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (series.metadata.publisher.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.series_detail_publisher_prefix, series.metadata.publisher),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (series.metadata.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.series_detail_genres_prefix,
                        series.metadata.genres.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (series.metadata.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = series.metadata.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                )
            }
        }
    }
}
