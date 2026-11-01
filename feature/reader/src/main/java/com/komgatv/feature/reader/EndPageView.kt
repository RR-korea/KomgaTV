package com.komgatv.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.displayName

/**
 * 책 경계 화면에서 갈 권을 어느 쪽에 둘지와 키 안내.
 *
 * 원칙: **갈 권은 그리로 가는 키가 있는 쪽에.** 왼쪽에서 오른쪽(LTR)으로 읽으면 다음 권은 ▶ 쪽(오른쪽),
 * 오른쪽에서 왼쪽(RTL, 만화)으로 읽으면 다음 권은 ◀ 쪽(왼쪽). 이전 권은 그 반대.
 * 세로 스크롤은 아래·오른쪽이 다음이라 LTR과 같은 배치에 ▼▲ 키.
 *
 * @property destinationOnLeft 갈 권(다음·이전 권)을 왼쪽에 둔다
 * @property goKey 갈 권으로 가는 키, [backKey] 지금 권으로 돌아가는 키
 */
internal data class BoundaryLayout(val destinationOnLeft: Boolean, val goKey: String, val backKey: String) {
    /** 지금 권 → 갈 권을 가리키는 화살표 */
    val arrow: String get() = if (destinationOnLeft) "‹" else "›"

    companion object {
        private const val LEFT = "◀"
        private const val RIGHT = "▶"
        private const val UP = "▲"
        private const val DOWN = "▼"

        /** @param toNext true면 책 끝 화면(다음 권), false면 책 첫 화면(이전 권) */
        fun of(toNext: Boolean, direction: ReadingDirection, continuous: Boolean): BoundaryLayout = when {
            continuous -> if (toNext) BoundaryLayout(false, DOWN, UP) else BoundaryLayout(true, UP, DOWN)
            direction == ReadingDirection.RIGHT_TO_LEFT ->
                if (toNext) BoundaryLayout(true, LEFT, RIGHT) else BoundaryLayout(false, RIGHT, LEFT)
            else -> if (toNext) BoundaryLayout(false, RIGHT, LEFT) else BoundaryLayout(true, LEFT, RIGHT)
        }
    }
}

/**
 * 책 끝 화면 — KMReader의 끝 페이지처럼 다 읽은 책과 다음 권을 나란히 보인다.
 * 다음 권 쪽 키(또는 OK)를 한 번 더 누르면 다음 권 첫 쪽으로 이어진다.
 */
@Composable
internal fun EndPageView(endPage: ReaderUiState.EndPage, layout: BoundaryLayout, modifier: Modifier = Modifier) {
    BoundaryPage(
        layout = layout,
        current = {
            BookSlot(
                label = stringResource(R.string.end_page_finished),
                title = endPage.finishedTitle,
                thumbnail = endPage.finishedThumbnail,
                coverWidth = 150.dp,
                dimmed = true,
            )
        },
        destination = {
            when {
                !endPage.nextResolved -> NextMessage(stringResource(R.string.end_page_finding_next))
                endPage.nextBook == null -> NextMessage(stringResource(R.string.end_page_last_book))
                else -> BookSlot(
                    label = stringResource(R.string.end_page_next),
                    title = endPage.nextBook.displayName(),
                    thumbnail = endPage.nextThumbnail,
                    coverWidth = 210.dp,
                    dimmed = false,
                )
            }
        },
        hint = if (endPage.nextBook != null) {
            stringResource(R.string.end_page_hint_next, layout.goKey)
        } else {
            stringResource(R.string.end_page_hint_last, layout.backKey)
        },
        modifier = modifier,
    )
}

/**
 * 책 첫 화면 — 책 끝 화면의 짝. 지금 권과 이전 권을 나란히 보이고,
 * 이전 권 쪽 키(또는 OK)를 한 번 더 누르면 이전 권 마지막 쪽으로 이어진다.
 */
@Composable
internal fun StartPageView(startPage: ReaderUiState.StartPage, layout: BoundaryLayout, modifier: Modifier = Modifier) {
    BoundaryPage(
        layout = layout,
        current = {
            BookSlot(
                label = stringResource(R.string.start_page_current),
                title = startPage.currentTitle,
                thumbnail = startPage.currentThumbnail,
                coverWidth = 150.dp,
                dimmed = true,
            )
        },
        destination = {
            when {
                !startPage.previousResolved -> NextMessage(stringResource(R.string.start_page_finding_previous))
                startPage.previousBook == null -> NextMessage(stringResource(R.string.start_page_first_book))
                else -> BookSlot(
                    label = stringResource(R.string.start_page_previous),
                    title = startPage.previousBook.displayName(),
                    thumbnail = startPage.previousThumbnail,
                    coverWidth = 210.dp,
                    dimmed = false,
                )
            }
        },
        hint = if (startPage.previousBook != null) {
            stringResource(R.string.start_page_hint_previous, layout.goKey)
        } else {
            stringResource(R.string.start_page_hint_first, layout.backKey)
        },
        modifier = modifier,
    )
}

/** 지금 권과 갈 권을 [BoundaryLayout] 순서로 놓고 그 사이에 화살표, 아래에 키 안내 */
@Composable
private fun BoundaryPage(
    layout: BoundaryLayout,
    current: @Composable () -> Unit,
    destination: @Composable () -> Unit,
    hint: String,
    modifier: Modifier,
) {
    Box(modifier = modifier.background(Color(0xFF0E1117)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (layout.destinationOnLeft) destination() else current()
                Text(text = layout.arrow, style = MaterialTheme.typography.displayMedium, color = Color(0xFF80B9FF))
                if (layout.destinationOnLeft) current() else destination()
            }
            Spacer(Modifier.height(36.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun BookSlot(label: String, title: String, thumbnail: String?, coverWidth: Dp, dimmed: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(coverWidth + 80.dp).alpha(if (dimmed) 0.6f else 1f),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = Color(0xFF80B9FF))
        Spacer(Modifier.height(10.dp))
        AsyncImage(
            model = thumbnail,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(coverWidth)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2A2A)),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NextMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(290.dp).padding(16.dp),
    )
}
