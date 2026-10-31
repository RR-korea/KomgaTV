package com.komgatv.core.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * 화면에 들어올 때·돌아올 때 줄 포커스
 *
 * TV에서는 포커스 없이 화면이 열리면 첫 키 입력이 이동 대신 포커스 잡기에 쓰인다.
 * 돌아왔을 때는 마지막으로 연 항목에 포커스를 준다. `focusRestorer`는 화면 전환을 넘지 못하고,
 * 포커스 변화로 기록하면 새로고침으로 순서가 바뀔 때 옆 카드로 밀린 포커스가 기록을 덮으므로
 * **열 때** 키를 기록한다. 그 항목이 없으면 [fallback](보통 첫 항목·첫 행)으로 간다.
 */
@Stable
class EntryFocusState internal constructor(openedKey: MutableState<String?>) {
    private var openedKey by openedKey
    private val items = mutableMapOf<String, FocusRequester>()

    /** 마지막으로 연 항목이 없을 때 포커스를 받을 곳 */
    val fallback = FocusRequester()

    /** 항목을 열기 직전에 부른다. 돌아오면 이 항목에 포커스를 준다. */
    fun onOpen(key: String) {
        openedKey = key
    }

    internal fun register(key: String, requester: FocusRequester) {
        items[key] = requester
    }

    internal fun unregister(key: String) {
        items.remove(key)
    }

    internal fun requestFocus() {
        (openedKey?.let(items::get) ?: fallback).requestFocus()
    }
}

/**
 * 화면 단위로 하나 만든다. 마지막으로 연 항목 키는 화면을 떠났다 돌아와도 남는다.
 * 로딩/오류 분기 바깥(화면 최상단)에서 불러야 한다.
 */
@Composable
fun rememberEntryFocusState(): EntryFocusState {
    val openedKey = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { EntryFocusState(openedKey) }
}

/** 항목별 FocusRequester. 화면에 있는 동안 [state]에 등록된다. */
@Composable
fun EntryFocusState.requesterFor(key: String): FocusRequester {
    val requester = remember(key) { FocusRequester() }
    DisposableEffect(this, key) {
        register(key, requester)
        onDispose { unregister(key) }
    }
    return requester
}

/**
 * 내용이 화면에 처음 나올 때와 [keys]가 바뀔 때 포커스를 준다.
 * 순서가 바뀔 수 있는 새로고침 결과를 [keys]로 넘긴다. 페이지를 덧붙이는 목록은 넘기지 않는다
 * (스크롤 중 포커스가 튄다).
 */
@Composable
fun EntryFocusEffect(state: EntryFocusState, vararg keys: Any?) {
    LaunchedEffect(state, *keys) {
        withFrameNanos { } // Lazy 항목이 배치된 뒤에 요청한다
        state.requestFocus()
    }
}

/**
 * 다른 화면(리더 등)에서 돌아올 때마다 [onReturn]을 부른다. 첫 진입에는 부르지 않는다.
 */
@Composable
fun OnReturnEffect(onReturn: () -> Unit) {
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (entered) onReturn() else entered = true
    }
}
