package com.komgatv.feature.library

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
import com.komgatv.core.model.Book
import com.komgatv.core.model.displayName

/**
 * 책 메뉴 (책 카드에서 OK를 길게) — 읽음으로 표시, 읽지 않음으로 표시, 분석. Back으로 닫는다.
 */
@Composable
internal fun BookActionsDialog(
    book: Book,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = book.displayName(), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Button(
                    onClick = { onMarkRead(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
                ) { Text(stringResource(R.string.book_action_mark_read)) }
                Button(
                    onClick = { onMarkUnread(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.book_action_mark_unread)) }
                Button(
                    onClick = { onAnalyze(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.book_action_analyze)) }
            }
        }
    }
}

/** 책 메뉴 결과 문구 — 화면 아래에 잠깐 */
@Composable
internal fun BookActionMessage(result: BookActionResult, onShown: () -> Unit) {
    LaunchedEffect(result) {
        kotlinx.coroutines.delay(2500)
        onShown()
    }
    Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = stringResource(
                when (result) {
                    BookActionResult.MarkedRead -> R.string.book_action_done_read
                    BookActionResult.MarkedUnread -> R.string.book_action_done_unread
                    BookActionResult.AnalyzeRequested -> R.string.book_action_done_analyze
                    BookActionResult.Failed -> R.string.book_action_failed
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
