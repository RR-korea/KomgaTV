package com.komgatv.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

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
