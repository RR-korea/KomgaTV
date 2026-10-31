package com.komgatv.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * TV에 최적화된 포스터 카드 컴포저블
 *
 * D-Pad 포커스 처리(Card의 확대·테두리), Coil 3 이미지 로딩을 포함합니다.
 * 만화/웹툰의 표지 이미지를 표시하는 데 사용됩니다.
 *
 * @param title 카드 하단에 표시될 제목
 * @param imageUrl 표지 이미지 URL
 * @param onClick 카드 클릭(선택) 시 콜백
 * @param modifier Modifier
 * @param subtitle 부제목 (선택, 예: "3/10권")
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PosterCard(
    title: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    // 이미지 요청을 remember하여 불필요한 재생성 방지
    val context = LocalContext.current
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(false)
            .build()
    }

    Column(
        modifier = modifier.width(150.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 포스터 이미지 카드
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f), // 만화 포스터 비율 (2:3)
            // 포커스 확대는 Card가 한다. 아래 제목을 덮지 않게 작게 키운다.
            scale = CardDefaults.scale(focusedScale = 1.05f),
            shape = CardDefaults.shape(
                shape = RoundedCornerShape(8.dp),
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(3.dp, Color(0xFF80B9FF)),
                    shape = RoundedCornerShape(8.dp),
                ),
            ),
        ) {
            // Card 내용은 크기를 갖지 않으므로 이미지가 직접 채운다 (크기 0이면 Coil이 요청하지 않는다)
            AsyncImage(
                model = imageRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 제목 텍스트
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 4.dp, end = 4.dp),
        )

        // 부제목 (선택)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}
