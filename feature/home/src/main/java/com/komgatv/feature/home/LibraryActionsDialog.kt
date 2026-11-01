package com.komgatv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.komgatv.core.model.Book
import com.komgatv.core.model.Library
import com.komgatv.core.model.cardTitle
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
                .ignoreHeldKey()
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

/**
 * "읽는 중"·"다음 권" 책 메뉴 (OK를 길게) — 이 시리즈를 두 줄에서 뺄지 묻는다.
 * [유지]에 먼저 포커스(실수로 지우지 않게), [제거]를 눌러야 뺀다. Back도 유지.
 */
@Composable
internal fun ReadingActionsDialog(
    book: Book,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .ignoreHeldKey()
                .width(480.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = book.cardTitle(), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    text = stringResource(R.string.reading_action_question),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).focusRequester(firstFocus),
                    ) { Text(stringResource(R.string.reading_action_keep)) }
                    Button(
                        onClick = { onHide(); onDismiss() },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.reading_action_remove)) }
                }
                Text(
                    text = stringResource(R.string.reading_action_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

/**
 * 메뉴를 연 길게 누름의 손 떼기(그리고 누르고 있는 동안의 반복)를 버린다.
 * 그대로 두면 메뉴가 열리자마자 포커스된 버튼이 눌린다. 메뉴 안에서 새로 누른 키부터 받는다.
 */
private fun Modifier.ignoreHeldKey(): Modifier = composed {
    var armed by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        when {
            armed -> false
            event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> {
                armed = true
                false
            }
            // 메뉴를 연 누름의 반복·손 떼기 — 버튼에 보내지 않는다
            else -> true
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
