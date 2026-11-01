package com.komgatv.feature.reader

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** 이보다 빨리 뜨는 쪽은 스피너 없이 바로 보인다 (깜빡임 방지) */
private const val SPINNER_DELAY_MILLIS = 250L

/**
 * 쪽(타일)을 아직 받는 중 — 빨리 넘길 때 검은 화면이 멈춘 것처럼 보이지 않게.
 * 잠깐 기다렸다가 돌기 시작한다.
 */
@Composable
internal fun PageLoadingIndicator(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPINNER_DELAY_MILLIS)
        visible = true
    }
    if (!visible) return

    val transition = rememberInfiniteTransition(label = "page-loading")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "page-loading-angle",
    )
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.size(48.dp)) {
            val stroke = 4.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.2f), 0f, 360f, useCenter = false, style = Stroke(stroke))
            drawArc(Color(0xFF4DA3FF), angle, 100f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(
            text = stringResource(R.string.reader_loading_page),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

/** 쪽(타일)을 받지 못함 — 다른 쪽으로 갔다 오면 다시 받는다 */
@Composable
internal fun PageLoadFailed(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.reader_page_load_failed),
        style = MaterialTheme.typography.bodyLarge,
        color = Color(0xFFFF8A80),
        textAlign = TextAlign.Center,
        modifier = modifier.padding(24.dp),
    )
}
