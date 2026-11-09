package com.komgatv.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalViewConfiguration

private val SELECT_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * 길게 눌러 연 메뉴용 — 메뉴를 연 OK 키를 아직 누르고 있는 동안(반복·떼기)에는 첫 버튼이 눌리지 않게 막는다.
 * OK를 한 번 뗀 뒤, 또는 다른 키를 누른 뒤부터 받는다.
 */
fun Modifier.ignoreHeldSelectKey(): Modifier = composed {
    var armed by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        when {
            armed -> false
            event.key in SELECT_KEYS -> {
                if (event.type == KeyEventType.KeyUp) armed = true
                true
            }
            else -> {
                armed = true
                false
            }
        }
    }
}

/**
 * 카드의 OK 누름·길게 누름을 직접 가른다 — 누른 시간을 재므로 누르는 동안 반복 키를 보내지 않는 리모컨에서도 길게 누름이 된다.
 * (TV Card의 onLongClick은 시스템이 반복 키에 붙이는 길게 누름 표시에만 기대 그런 리모컨에서는 그냥 열린다)
 */
fun Modifier.selectKeyClicks(onClick: () -> Unit, onLongClick: () -> Unit): Modifier = composed {
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    // 이 카드에서 누르기 시작한 시각 (-1: 누르고 있지 않음) — 다른 화면에서 누른 OK를 여기서 뗀 것은 버린다
    var downAt by remember { mutableLongStateOf(-1L) }
    var fired by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        if (event.key !in SELECT_KEYS) return@onPreviewKeyEvent false
        val native = event.nativeKeyEvent
        when (event.type) {
            KeyEventType.KeyDown -> if (native.repeatCount == 0) {
                downAt = native.eventTime
                fired = false
            } else if (downAt >= 0 && !fired && (native.isLongPress || native.eventTime - downAt >= timeout)) {
                fired = true
                longClick()
            }
            KeyEventType.KeyUp -> {
                if (downAt >= 0 && !fired) {
                    if (native.eventTime - downAt >= timeout) longClick() else click()
                }
                downAt = -1L
            }
        }
        true
    }
}
