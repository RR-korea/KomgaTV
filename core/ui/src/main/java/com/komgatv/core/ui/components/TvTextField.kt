package com.komgatv.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * TV용 한 줄 입력칸
 *
 * tv-material3에는 TextField가 없어 BasicTextField에 TV 테마 색과 포커스 테두리를 입힌다.
 *
 * D-pad 포커스는 바깥 상자가 받고, OK를 누르면(손을 뗄 때) 그때 입력칸으로 들어가 키보드가 뜬다.
 * BasicTextField가 바로 포커스를 받으면 지나가기만 해도 키보드가 떠서 이후 D-pad 키를
 * 키보드가 가져가고, 누를 때 열면 뒤따르는 키 떼기가 키보드로 가서 글자가 입력된다.
 * 입력 중 키보드를 닫은 뒤의 위/아래, 키보드의 다음 버튼은 입력을 끝내고 포커스를 옮긴다.
 * 키보드를 닫은 뒤의 Back은 화면을 떠나지 않고 입력만 끝낸다.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val boxFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)

    fun finishAndMove(direction: FocusDirection): Boolean {
        editing = false
        return focusManager.moveFocus(direction)
    }

    LaunchedEffect(editing) {
        if (editing) fieldFocus.requestFocus()
    }

    // 키보드가 열려 있으면 Back은 키보드가 먼저 받아 닫는다. 그다음 Back은 화면을 떠나지 않고 입력만 끝낸다.
    // (targetSdk 36은 예측형 뒤로 가기라 Back이 키 이벤트로 오지 않는다 — BackHandler로 받는다)
    BackHandler(enabled = editing) {
        editing = false
        boxFocus.requestFocus()
    }

    Box(
        modifier = modifier
            .focusRequester(boxFocus)
            .onFocusChanged { focused = it.hasFocus }
            .onKeyEvent { event ->
                val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (!isOk || editing) return@onKeyEvent false
                if (event.type == KeyEventType.KeyUp) editing = true
                true
            }
            .focusable()
            .background(colors.surfaceVariant, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.primary else colors.border, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { finishAndMove(FocusDirection.Down) }),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .focusProperties { canFocus = editing }
                .onFocusChanged { if (!it.isFocused) editing = false }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> finishAndMove(FocusDirection.Down)
                        Key.DirectionUp -> finishAndMove(FocusDirection.Up)
                        else -> false
                    }
                },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
    }
}
