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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import com.komgatv.feature.reader.EndPageView
import com.komgatv.feature.reader.ReaderUiState
import timber.log.Timber

private const val END_KEY = "end"

/**
 * 세로 스크롤 리더 본문. 항목 하나가 타일 하나이고, 보이는 타일만 디코딩한다.
 *
 * @param scrollToTileIndex 이 값이 오면 그 타일로 스크롤한 뒤 [onScrolled]
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
    modifier: Modifier = Modifier,
) {
    val currentOnVisible by rememberUpdatedState(onVisibleTilesChanged)
    val currentOnEndVisible by rememberUpdatedState(onEndVisible)

    LaunchedEffect(scrollToTileIndex) {
        if (scrollToTileIndex != null) {
            listState.scrollToItem(scrollToTileIndex)
            onScrolled()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key as String } }
            .distinctUntilChanged()
            .collect { keys ->
                currentOnVisible(keys)
                currentOnEndVisible(END_KEY in keys)
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(tiles, key = { it.key }) { tile ->
            TileItem(tile, loadTile, filterQuality)
        }
        // 책 끝 카드 — KMReader처럼 마지막 쪽 다음에 다음 권을 보인다 (아래 한 번 더 = 다음 권)
        item(key = END_KEY) {
            // 책 끝 카드는 화면 한 장 높이 (창 크기는 스크롤 중에 바뀌지 않는다)
            val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
            val info = endPage ?: ReaderUiState.EndPage("", null, null, null, nextResolved = false)
            EndPageView(info, Modifier.fillMaxWidth().height(screenHeight.coerceAtLeast(400.dp)))
        }
    }
}

@Composable
private fun TileItem(tile: PageTile, loadTile: suspend (PageTile) -> ImageBitmap, filterQuality: FilterQuality) {
    val density = LocalDensity.current
    val bitmap by produceState<ImageBitmap?>(null, tile, loadTile) {
        value = if (tile.isPlaceholder) {
            null
        } else {
            try {
                loadTile(tile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 화면에서 벗어나 취소되면 OkHttp는 InterruptedIOException을 던진다 — 그때는 기록하지 않는다
                currentCoroutineContext().ensureActive()
                Timber.w(e, "타일 불러오기 실패: ${tile.key}")
                null
            }
        }
    }
    Column {
        Box(
            modifier = with(density) { Modifier.size(tile.displayWidth.toDp(), tile.displayHeight.toDp()) },
        ) {
            bitmap?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = filterQuality,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (tile.bottomSpacingDp > 0) {
            Spacer(modifier = Modifier.height(tile.bottomSpacingDp.dp))
        }
    }
}
