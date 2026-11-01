package com.komgatv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.komgatv.core.model.Library
import kotlinx.coroutines.delay

/**
 * 라이브러리 메뉴 (라이브러리 카드에서 OK를 길게) — 스캔, 휴지통 비우기 (둘 다 관리자). Back으로 닫는다.
 */
@Composable
internal fun LibraryActionsDialog(
    library: Library,
    onScan: () -> Unit,
    onEmptyTrash: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(440.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = library.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Button(
                    onClick = { onScan(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
                ) { Text(stringResource(R.string.library_action_scan)) }
                Button(
                    onClick = { onEmptyTrash(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.library_action_empty_trash)) }
                Text(
                    text = stringResource(R.string.library_action_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

/** 라이브러리 메뉴 결과 문구 — 화면 아래에 잠깐 */
@Composable
internal fun LibraryActionMessage(result: LibraryActionResult, onShown: () -> Unit) {
    LaunchedEffect(result) {
        delay(3000)
        onShown()
    }
    Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = stringResource(
                when (result) {
                    LibraryActionResult.ScanRequested -> R.string.library_action_done_scan
                    LibraryActionResult.TrashEmptied -> R.string.library_action_done_trash
                    LibraryActionResult.Failed -> R.string.library_action_failed
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier
                .background(Color(0xE0303030), RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
