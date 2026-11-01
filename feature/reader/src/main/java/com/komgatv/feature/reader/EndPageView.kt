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
import com.komgatv.core.model.displayName

/**
 * 책 끝 화면 — KMReader의 끝 페이지처럼 다 읽은 책과 다음 권을 나란히 보인다.
 * 앞으로(다음 쪽 키·OK) 한 번 더 누르면 다음 권 첫 쪽으로 이어진다.
 */
@Composable
internal fun EndPageView(endPage: ReaderUiState.EndPage, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0xFF0E1117)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookSlot(
                    label = stringResource(R.string.end_page_finished),
                    title = endPage.finishedTitle,
                    thumbnail = endPage.finishedThumbnail,
                    coverWidth = 150.dp,
                    dimmed = true,
                )
                Text(text = "›", style = MaterialTheme.typography.displayMedium, color = Color(0xFF80B9FF))
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
            }
            Spacer(Modifier.height(36.dp))
            Text(
                text = stringResource(
                    if (endPage.nextBook != null) R.string.end_page_hint_next else R.string.end_page_hint_last,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * 책 첫 화면 — 책 끝 화면의 거울. 이전 권과 지금 권을 나란히 보이고,
 * 뒤로(이전 쪽 키·OK) 한 번 더 누르면 이전 권 마지막 쪽으로 이어진다.
 */
@Composable
internal fun StartPageView(startPage: ReaderUiState.StartPage, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0xFF0E1117)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                Text(text = "‹", style = MaterialTheme.typography.displayMedium, color = Color(0xFF80B9FF))
                BookSlot(
                    label = stringResource(R.string.start_page_current),
                    title = startPage.currentTitle,
                    thumbnail = startPage.currentThumbnail,
                    coverWidth = 150.dp,
                    dimmed = true,
                )
            }
            Spacer(Modifier.height(36.dp))
            Text(
                text = stringResource(
                    if (startPage.previousBook != null) R.string.start_page_hint_previous else R.string.start_page_hint_first,
                ),
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
