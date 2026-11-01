package com.komgatv.feature.reader.continuous

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import com.komgatv.core.model.ReadingDirection
import com.komgatv.feature.reader.BoundaryLayout
import com.komgatv.feature.reader.EndPageView
import com.komgatv.feature.reader.PageLoadFailed
import com.komgatv.feature.reader.PageLoadingIndicator
import com.komgatv.feature.reader.ReaderUiState
import com.komgatv.feature.reader.StartPageView
import timber.log.Timber

private const val START_KEY = "start"
private const val END_KEY = "end"

/**
 * 세로 스크롤 리더 본문. 항목 하나가 타일 하나이고, 보이는 타일만 디코딩한다.
 *
 * 맨 위에 책 첫 카드, 맨 아래에 책 끝 카드가 있어 항목 번호 = 타일 번호 + 1.
 *
 * @param scrollToTileIndex 이 값이 오면 그 타일로 스크롤한 뒤 [onScrolled]. -1은 책 첫 카드, 타일 수는 책 끝 카드
 * @param onVisibleTilesChanged 보이는 타일의 [PageTile.key] 목록 (위에서부터)
 */
@Composable
internal fun ContinuousPages(
    tiles: List<PageTile>,
    listState: LazyListState,
    filterQuality: FilterQuality,
    scrollToTileIndex: Int?,
    loadTile: suspend (PageTile) -> ImageBitmap,
    onScrolled: () -> Unit,
    onVisibleTilesChanged: (List<String>) -> Unit,
    endPage: ReaderUiState.EndPage?,
    onEndVisible: (Boolean) -> Unit,
    startPage: ReaderUiState.StartPage?,
    onStartVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnVisible by rememberUpdatedState(onVisibleTilesChanged)
    val currentOnEndVisible by rememberUpdatedState(onEndVisible)
    val currentOnStartVisible by rememberUpdatedState(onStartVisible)

    LaunchedEffect(scrollToTileIndex) {
        if (scrollToTileIndex != null) {
            listState.scrollToItem(scrollToTileIndex + 1)
            onScrolled()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key as String } }
            .distinctUntilChanged()
            .collect { keys ->
                currentOnVisible(keys)
                currentOnEndVisible(END_KEY in keys)
                currentOnStartVisible(START_KEY in keys)
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 책 첫 카드 — 1쪽 위에 이전 권을 보인다 (위 한 번 더 = 이전 권 끝)
        item(key = START_KEY) {
            val info = startPage ?: ReaderUiState.StartPage("", null, null, null, previousResolved = false)
            StartPageView(info, BoundaryLayout.of(toNext = false, ReadingDirection.LEFT_TO_RIGHT, continuous = true), Modifier.fillMaxWidth().height(boundaryCardHeight()))
        }
        items(tiles, key = { it.key }) { tile ->
            TileItem(tile, loadTile, filterQuality)
        }
        // 책 끝 카드 — KMReader처럼 마지막 쪽 다음에 다음 권을 보인다 (아래 한 번 더 = 다음 권)
        item(key = END_KEY) {
            val info = endPage ?: ReaderUiState.EndPage("", null, null, null, nextResolved = false)
            EndPageView(info, BoundaryLayout.of(toNext = true, ReadingDirection.LEFT_TO_RIGHT, continuous = true), Modifier.fillMaxWidth().height(boundaryCardHeight()))
        }
    }
}

/** 책 첫·끝 카드는 화면 한 장 높이 (창 크기는 스크롤 중에 바뀌지 않는다) */
@Composable
private fun boundaryCardHeight(): Dp {
    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    return screenHeight.coerceAtLeast(400.dp)
}

/** 타일 하나를 불러온 결과 */
private sealed interface TileLoad {
    data object Loading : TileLoad
    data class Loaded(val bitmap: ImageBitmap) : TileLoad
    data object Failed : TileLoad
}

@Composable
private fun TileItem(tile: PageTile, loadTile: suspend (PageTile) -> ImageBitmap, filterQuality: FilterQuality) {
    val density = LocalDensity.current
    // 크기를 모르는 쪽(자리 표시)은 크기를 알아내 다시 계획될 때까지 불러오는 중으로 둔다
    val load by produceState<TileLoad>(TileLoad.Loading, tile, loadTile) {
        if (tile.isPlaceholder) return@produceState
        value = try {
            TileLoad.Loaded(loadTile(tile))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 화면에서 벗어나 취소되면 OkHttp는 InterruptedIOException을 던진다 — 그때는 기록하지 않는다
            currentCoroutineContext().ensureActive()
            Timber.w(e, "타일 불러오기 실패: ${tile.key}")
            TileLoad.Failed
        }
    }
    Column {
        Box(
            modifier = with(density) { Modifier.size(tile.displayWidth.toDp(), tile.displayHeight.toDp()) },
            contentAlignment = Alignment.Center,
        ) {
            when (val state = load) {
                is TileLoad.Loaded -> Image(
                    state.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = filterQuality,
                    modifier = Modifier.fillMaxSize(),
                )
                // 쪽의 첫 조각에만 — 긴 쪽이 여러 조각이면 스피너가 줄줄이 보이지 않게
                TileLoad.Loading -> if (tile.tileIndex == 0) PageLoadingIndicator()
                TileLoad.Failed -> PageLoadFailed()
            }
        }
        if (tile.bottomSpacingDp > 0) {
            Spacer(modifier = Modifier.height(tile.bottomSpacingDp.dp))
        }
    }
}
