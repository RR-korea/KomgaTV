package com.komgatv.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.tv.material3.MaterialTheme

/**
 * 가로 진행 막대 (tv-material3에 LinearProgressIndicator가 없어 직접 그린다). progress는 0..1
 *
 * @param fromEnd 오른쪽에서 왼쪽으로 찬다 (오른쪽에서 왼쪽으로 읽는 책)
 */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, fromEnd: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = if (fromEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
