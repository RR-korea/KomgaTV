package com.komgatv.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
 * 책 화면은 어둡게 덮지 않는다 — 대비·밝기·화질을 바꾸면 패널 왼쪽 책에서 바로 확인한다.
 *
 * @param seriesCustomized 이 작품에 따로 저장된 설정이 있는지 — 없으면 "앱 기본값 사용 중"
 * @param hidablePages 지금 화면의 쪽 중 숨길 수 있는 쪽 — 쪽마다 [N쪽 숨기기] 버튼 (이 책에서만, 이 기기에만)
 * @param hiddenPageCount 이 책에서 숨긴 쪽 수 — 있으면 [숨긴 쪽 되돌리기]
 */
@Composable
fun ReaderSettingsPanel(
    settings: ReaderSettings,
    layoutOffset: Boolean,
    seriesCustomized: Boolean,
    onChange: (ReaderSettings) -> Unit,
    onLayoutOffsetChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    hidablePages: List<Int> = emptyList(),
    hiddenPageCount: Int = 0,
    onHidePage: (Int) -> Unit = {},
    onRestoreHiddenPages: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    // [숨긴 쪽 되돌리기]를 누르면 그 버튼이 사라진다 — 포커스를 [N쪽 숨기기]로 옮긴다
    val hideFocus = remember { FocusRequester() }
    var restorePressed by remember { mutableStateOf(false) }
    LaunchedEffect(hiddenPageCount) {
        if (hiddenPageCount == 0 && restorePressed) {
            restorePressed = false
            runCatching { hideFocus.requestFocus() }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
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
            // 쪽 숨기기 — 광고·빈 쪽 등을 이 책에서 건너뛴다 (서버 파일은 그대로)
            if (hidablePages.isNotEmpty() || hiddenPageCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    hidablePages.forEachIndexed { index, page ->
                        OutlinedButton(
                            onClick = { onHidePage(page) },
                            modifier = if (index == 0) Modifier.focusRequester(hideFocus) else Modifier,
                        ) {
                            Text(stringResource(R.string.reader_hide_page, page))
                        }
                    }
                    if (hiddenPageCount > 0) {
                        OutlinedButton(onClick = {
                            restorePressed = true
                            onRestoreHiddenPages()
                        }) {
                            Text(stringResource(R.string.reader_restore_hidden_pages, hiddenPageCount))
                        }
                    }
                }
            }
            // 되돌리기 버튼과 키 안내를 한 줄에 — 항목이 늘어도 한 화면에 들어가게
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
}
