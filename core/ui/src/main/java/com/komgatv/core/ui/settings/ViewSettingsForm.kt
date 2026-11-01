package com.komgatv.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.komgatv.core.model.ImageEnhancement
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.PageTransition
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.model.ReaderType
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import com.komgatv.core.ui.R

/** 좌우 여백 단계: 0%~45%, 5% 단위 */
private val PADDING_STEPS = (0..9).map { it * 0.05f }

/** 쪽 간격 단계: 0~64dp, 8 단위 */
private val SPACING_STEPS = (0..8).map { it * 8 }

/**
 * 보기 설정 항목들 (Komelia와 같은 이름) — 서버 설정의 "보기 기본값"과 리더의 작품 보기 설정이 함께 쓴다.
 *
 * 한 줄에 항목 하나(왼쪽 이름, 오른쪽 값)라 TV 한 화면에 다 들어간다. 위/아래 = 항목, 좌/우·OK = 값 바꾸기 (바로 반영).
 *
 * @param layoutOffset 쪽 맞춤 밀기 — 리더에서만(세션 동안만) 보인다. null이면 줄을 그리지 않는다.
 * @param showZoom 확대(줌인) 줄 — 작품별 설정(리더)에서만 보인다.
 */
@Composable
fun ViewSettingsForm(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
    layoutOffset: Boolean? = null,
    onLayoutOffsetChange: (Boolean) -> Unit = {},
    showZoom: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        OptionRow(
            title = stringResource(R.string.view_reader_type),
            options = listOf(
                ReaderType.PAGED to stringResource(R.string.view_reader_type_paged),
                ReaderType.CONTINUOUS to stringResource(R.string.view_reader_type_continuous),
            ),
            current = settings.readerType,
            onChange = { onChange(settings.copy(readerType = it)) },
            modifier = if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
        )
        when (settings.readerType) {
            ReaderType.PAGED -> {
                OptionRow(
                    title = stringResource(R.string.view_layout),
                    options = listOf(
                        PageLayout.SINGLE_PAGE to stringResource(R.string.view_layout_single),
                        PageLayout.DOUBLE_PAGES to stringResource(R.string.view_layout_double),
                        PageLayout.DOUBLE_PAGES_NO_COVER to stringResource(R.string.view_layout_double_no_cover),
                    ),
                    current = settings.pageLayout,
                    onChange = { onChange(settings.copy(pageLayout = it)) },
                )
                if (layoutOffset != null) {
                    OnOffRow(
                        title = stringResource(R.string.view_offset),
                        value = layoutOffset,
                        onChange = onLayoutOffsetChange,
                        // 쪽 맞춤 밀기는 두 쪽 보기에서만
                        enabled = settings.pageLayout != PageLayout.SINGLE_PAGE,
                    )
                }
                OptionRow(
                    title = stringResource(R.string.view_direction),
                    options = listOf(
                        ReadingDirection.LEFT_TO_RIGHT to stringResource(R.string.view_direction_ltr),
                        ReadingDirection.RIGHT_TO_LEFT to stringResource(R.string.view_direction_rtl),
                    ),
                    current = settings.readingDirection,
                    onChange = { onChange(settings.copy(readingDirection = it)) },
                )
                OptionRow(
                    title = stringResource(R.string.view_scale),
                    options = listOf(
                        ScaleType.SCREEN to stringResource(R.string.view_scale_screen),
                        ScaleType.FIT_WIDTH to stringResource(R.string.view_scale_fit_width),
                        ScaleType.FIT_HEIGHT to stringResource(R.string.view_scale_fit_height),
                        ScaleType.ORIGINAL to stringResource(R.string.view_scale_original),
                    ),
                    current = settings.scaleType,
                    onChange = { onChange(settings.copy(scaleType = it)) },
                )
                OptionRow(
                    title = stringResource(R.string.view_transition),
                    options = listOf(
                        PageTransition.OFF to stringResource(R.string.view_transition_off),
                        PageTransition.SLIDE to stringResource(R.string.view_transition_slide),
                        PageTransition.FADE to stringResource(R.string.view_transition_fade),
                    ),
                    current = settings.pageTransition,
                    onChange = { onChange(settings.copy(pageTransition = it)) },
                )
                if (showZoom) {
                    // 크기 맞춤 결과를 1%씩 더 확대 — 길게 누르면 키 반복으로 빨리 바뀐다
                    SettingRow(
                        title = stringResource(R.string.view_zoom),
                        value = stringResource(R.string.view_percent_value, settings.zoomPercent),
                        onPrevious = { onChange(settings.copy(zoomPercent = stepZoom(settings.zoomPercent, -1))) },
                        onNext = { onChange(settings.copy(zoomPercent = stepZoom(settings.zoomPercent, 1))) },
                    )
                }
            }

            ReaderType.CONTINUOUS -> {
                OptionRow(
                    title = stringResource(R.string.view_side_padding),
                    options = PADDING_STEPS.map { it to stringResource(R.string.view_percent_value, (it * 100).toInt()) },
                    current = PADDING_STEPS.minBy { kotlin.math.abs(it - settings.continuousPadding) },
                    onChange = { onChange(settings.copy(continuousPadding = it)) },
                    wrap = false,
                )
                OptionRow(
                    title = stringResource(R.string.view_page_spacing),
                    options = SPACING_STEPS.map { it to stringResource(R.string.view_dp_value, it) },
                    current = SPACING_STEPS.minBy { kotlin.math.abs(it - settings.continuousPageSpacing) },
                    onChange = { onChange(settings.copy(continuousPageSpacing = it)) },
                    wrap = false,
                )
            }
        }
        OptionRow(
            title = stringResource(R.string.view_enhancement),
            options = listOf(
                ImageEnhancement.OFF to stringResource(R.string.view_enhancement_off),
                ImageEnhancement.SMOOTH to stringResource(R.string.view_enhancement_smooth),
                ImageEnhancement.SHARPEN to stringResource(R.string.view_enhancement_sharpen),
                ImageEnhancement.SHARPEN_STRONG to stringResource(R.string.view_enhancement_sharpen_strong),
            ),
            current = settings.imageEnhancement,
            onChange = { onChange(settings.copy(imageEnhancement = it)) },
        )
    }
}

private fun stepZoom(current: Int, delta: Int): Int =
    (current + delta).coerceIn(ReaderSettings.MIN_ZOOM_PERCENT, ReaderSettings.MAX_ZOOM_PERCENT)

/**
 * 값 목록 중 하나를 고르는 줄. 좌 = 이전 값, 우·OK = 다음 값.
 * [wrap]이 false면 끝에서 멈춘다 (여백·간격처럼 크기가 있는 값).
 */
@Composable
private fun <T> OptionRow(
    title: String,
    options: List<Pair<T, String>>,
    current: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    wrap: Boolean = true,
) {
    val index = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
    fun step(delta: Int) {
        val next = if (wrap) (index + delta).mod(options.size) else (index + delta).coerceIn(0, options.lastIndex)
        if (next != index) onChange(options[next].first)
    }
    SettingRow(title, options[index].second, onPrevious = { step(-1) }, onNext = { step(1) }, modifier, enabled)
}

@Composable
private fun OnOffRow(title: String, value: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    SettingRow(
        title = title,
        value = stringResource(if (value) R.string.view_on else R.string.view_off),
        onPrevious = { onChange(!value) },
        onNext = { onChange(!value) },
        enabled = enabled,
    )
}

/** 한 줄: 왼쪽 이름, 오른쪽 "‹ 값 ›". 포커스되면 테두리. */
@Composable
private fun SettingRow(
    title: String,
    value: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Color(0xFF2E2E2E) else Color(0xFF242424))
            .border(2.dp, if (focused) MaterialTheme.colorScheme.border else Color.Transparent, shape)
            .focusable(enabled = enabled, interactionSource = interaction)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onPrevious(); true }
                    Key.DirectionRight, Key.DirectionCenter, Key.Enter -> { onNext(); true }
                    else -> false
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray.copy(alpha = alpha),
        )
        Text(
            text = if (enabled) "‹  $value  ›" else value,
            style = MaterialTheme.typography.bodyLarge,
            color = (if (focused) Color.White else Color(0xFFE0E0E0)).copy(alpha = alpha),
        )
    }
}
