package com.komgatv.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Carousel
import androidx.tv.material3.CarouselDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.rememberCarouselState
import coil3.compose.AsyncImage
import com.komgatv.core.model.Book
import com.komgatv.core.model.Library
import com.komgatv.core.model.Series
import com.komgatv.core.network.ServerSessionManager
import com.komgatv.core.ui.components.PosterCard
import com.komgatv.core.ui.components.TVRow

/**
 * TV 홈 화면
 *
 * 앱의 메인 화면으로 다음 섹션을 포함합니다:
 * - 상단 히어로 캐러셀/배너 (추천 또는 계속 읽기 시리즈)
 * - "계속 읽기" 행
 * - "최근 추가" 행
 * - "라이브러리" 행
 *
 * @param onNavigateToLibrary 라이브러리 화면으로 이동
 * @param onNavigateToSeries 시리즈 상세 화면으로 이동
 * @param onNavigateToReader 리더 화면으로 이동
 * @param onNavigateToSearch 검색 화면으로 이동
 * @param onNavigateToSettings 설정 화면으로 이동
 * @param viewModel 홈 화면 ViewModel
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLibrary: (String) -> Unit,
    onNavigateToSeries: (String) -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is HomeUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "로딩 중...",
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
                        text = "오류가 발생했습니다",
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

        is HomeUiState.Success -> {
            HomeContent(
                continueReading = state.continueReading,
                recentlyAdded = state.recentlyAdded,
                libraries = state.libraries,
                featuredSeries = state.featuredSeries,
                baseUrl = viewModel.baseUrl,
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToSeries = onNavigateToSeries,
                onNavigateToReader = onNavigateToReader,
            )
        }
    }
}

/**
 * 홈 화면 콘텐츠
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeContent(
    continueReading: List<Book>,
    recentlyAdded: List<Series>,
    libraries: List<Library>,
    featuredSeries: List<Series>,
    baseUrl: String,
    onNavigateToLibrary: (String) -> Unit,
    onNavigateToSeries: (String) -> Unit,
    onNavigateToReader: (String) -> Unit,
) {
    // D4: 표준 LazyColumn 사용
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        // ========== 히어로 캐러셀 배너 ==========
        if (featuredSeries.isNotEmpty()) {
            item {
                val carouselState = rememberCarouselState()
                Carousel(
                    itemCount = featuredSeries.size,
                    carouselState = carouselState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(376.dp)
                        .padding(horizontal = 48.dp, vertical = 24.dp),
                    carouselIndicator = {
                        CarouselDefaults.IndicatorRow(
                            itemCount = featuredSeries.size,
                            activeItemIndex = carouselState.activeItemIndex,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                        )
                    },
                ) { index ->
                    val series = featuredSeries[index]
                    HeroBannerItem(
                        series = series,
                        baseUrl = baseUrl,
                        onClick = { onNavigateToSeries(series.id) },
                    )
                }
            }
        }

        // ========== "계속 읽기" 행 ==========
        if (continueReading.isNotEmpty()) {
            item {
                TVRow(
                    title = "계속 읽기",
                    items = continueReading,
                    key = { it.id },
                ) { book ->
                    PosterCard(
                        title = book.name,
                        imageUrl = buildBookThumbnailUrl(baseUrl, book.id),
                        subtitle = "${book.readProgress?.page ?: 0}/${book.pageCount}페이지",
                        onClick = { onNavigateToReader(book.id) },
                    )
                }
            }
        }

        // ========== "최근 추가" 행 ==========
        if (recentlyAdded.isNotEmpty()) {
            item {
                TVRow(
                    title = "최근 추가",
                    items = recentlyAdded,
                    key = { it.id },
                ) { series ->
                    PosterCard(
                        title = series.name,
                        imageUrl = buildSeriesThumbnailUrl(baseUrl, series.id),
                        subtitle = "${series.booksCount}권",
                        onClick = { onNavigateToSeries(series.id) },
                    )
                }
            }
        }

        // ========== "라이브러리" 행 ==========
        if (libraries.isNotEmpty()) {
            item {
                TVRow(
                    title = "라이브러리",
                    items = libraries,
                    key = { it.id },
                ) { library ->
                    PosterCard(
                        title = library.name,
                        imageUrl = "",
                        onClick = { onNavigateToLibrary(library.id) },
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
 * 히어로 배너 아이템
 */
@Composable
private fun HeroBannerItem(
    series: Series,
    baseUrl: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart,
    ) {
        AsyncImage(
            model = buildSeriesThumbnailUrl(baseUrl, series.id),
            contentDescription = series.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        ) {
            Text(
                text = series.name,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (series.metadata.summary.isNotEmpty()) {
                Text(
                    text = series.metadata.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                text = "${series.booksCount}권",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ========== 썸네일 URL 빌더 (세션 baseUrl 기반 절대 경로) ==========

private fun buildSeriesThumbnailUrl(baseUrl: String, seriesId: String): String {
    if (baseUrl.isBlank()) return ""
    return "${baseUrl.trimEnd('/')}/api/v1/series/$seriesId/thumbnail"
}

private fun buildBookThumbnailUrl(baseUrl: String, bookId: String): String {
    if (baseUrl.isBlank()) return ""
    return "${baseUrl.trimEnd('/')}/api/v1/books/$bookId/thumbnail"
}
