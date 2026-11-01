package com.komgatv.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.ui.settings.ViewSettingsForm

/**
 * 리더 보기 설정 패널 — **이 작품(시리즈)에만** 저장된다. 앱 전체 기본값은 서버 설정 화면에서 바꾼다.
 *
 * 한 화면에 다 들어가게(스크롤 없음) 한 줄짜리 항목을 쓴다. 열릴 때 첫 항목에 포커스, Back은 리더가 받아 닫는다.
 *
 * @param seriesCustomized 이 작품에 따로 저장된 설정이 있는지 — 없으면 "앱 기본값 사용 중"
 */
@Composable
fun ReaderSettingsPanel(
    settings: ReaderSettings,
    layoutOffset: Boolean,
    seriesCustomized: Boolean,
    onChange: (ReaderSettings) -> Unit,
    onLayoutOffsetChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(500.dp)
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_settings_title_series),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                text = stringResource(
                    if (seriesCustomized) R.string.reader_settings_series_custom else R.string.reader_settings_series_default,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (seriesCustomized) Color(0xFF80B9FF) else Color.Gray,
            )
            ViewSettingsForm(
                settings = settings,
                onChange = onChange,
                firstFocus = firstFocus,
                layoutOffset = layoutOffset,
                onLayoutOffsetChange = onLayoutOffsetChange,
                showZoom = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (seriesCustomized) {
                OutlinedButton(onClick = onReset) {
                    Text(stringResource(R.string.reader_settings_reset))
                }
            }
            Text(
                text = stringResource(R.string.reader_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }
    }
}
