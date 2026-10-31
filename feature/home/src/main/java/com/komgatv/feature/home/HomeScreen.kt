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
                }
            }
        }

        is HomeUiState.Success -> {
            HomeContent(
                continueReading = state.continueReading,
                libraries = state.libraries,
                imageUrls = viewModel.imageUrls,
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToReader = onNavigateToReader,
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
    onNavigateToLibrary: (String) -> Unit,
    onNavigateToReader: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer(),
        contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
    ) {
        // ========== "읽는 중" 행 ==========
        if (continueReading.isNotEmpty()) {
            item(key = "home_continue_reading_row") {
                TVRow(
                    title = stringResource(R.string.home_continue_reading),
                    items = continueReading,
                    key = { it.id },
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
                    PosterCard(
                        title = book.name,
                        imageUrl = imageUrls.bookThumbnail(book.id).orEmpty(),
                        subtitle = progressSubtitle,
                        onClick = { onNavigateToReader(book.id) },
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
