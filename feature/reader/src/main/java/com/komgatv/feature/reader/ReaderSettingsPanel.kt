package com.komgatv.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.ReaderType
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType

/**
 * 리더 보기 설정 패널
 *
 * 첫 줄은 리더 종류(Paged/Continuous), 그 아래 줄은 종류에 따라 바뀐다 (Komelia와 같은 항목).
 * - 상/하: 항목 이동, 좌/우 또는 OK: 값 바꾸기 (바로 반영)
 * - 열릴 때 첫 항목에 포커스, Back은 리더 화면이 받아 패널을 닫는다
 */
@Composable
fun ReaderSettingsPanel(
    readerType: ReaderType,
    pageLayout: PageLayout,
    layoutOffset: Boolean,
    readingDirection: ReadingDirection,
    scaleType: ScaleType,
    stretchToFit: Boolean,
    continuousPadding: Float,
    continuousPageSpacing: Int,
    onReaderTypeChange: (ReaderType) -> Unit,
    onPageLayoutChange: (PageLayout) -> Unit,
    onLayoutOffsetChange: (Boolean) -> Unit,
    onReadingDirectionChange: (ReadingDirection) -> Unit,
    onScaleTypeChange: (ScaleType) -> Unit,
    onStretchToFitChange: (Boolean) -> Unit,
    onContinuousPaddingChange: (Float) -> Unit,
    onContinuousPageSpacingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(420.dp)
                .background(Color(0xFF1E1E1E))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )

            OptionRow(
                title = stringResource(R.string.reader_type_title),
                options = listOf(
                    ReaderType.PAGED to stringResource(R.string.reader_type_paged),
                    ReaderType.CONTINUOUS to stringResource(R.string.reader_type_continuous),
                ),
                current = readerType,
                onChange = onReaderTypeChange,
                modifier = Modifier.focusRequester(firstFocusRequester),
            )

            when (readerType) {
                ReaderType.PAGED -> {
                    OptionRow(
                        title = stringResource(R.string.reader_layout_title),
                        options = listOf(
                            PageLayout.SINGLE_PAGE to stringResource(R.string.reader_layout_single),
                            PageLayout.DOUBLE_PAGES to stringResource(R.string.reader_layout_double),
                            PageLayout.DOUBLE_PAGES_NO_COVER to stringResource(R.string.reader_layout_double_no_cover),
                        ),
                        current = pageLayout,
                        onChange = onPageLayoutChange,
                    )
                    // 쪽 맞춤 밀기는 두 쪽 보기에서만
                    OnOffRow(
                        title = stringResource(R.string.reader_offset_title),
                        value = layoutOffset,
                        onChange = onLayoutOffsetChange,
                        enabled = pageLayout != PageLayout.SINGLE_PAGE,
                    )
                    OptionRow(
                        title = stringResource(R.string.reader_direction_title),
                        options = listOf(
                            ReadingDirection.LEFT_TO_RIGHT to stringResource(R.string.reader_direction_ltr),
                            ReadingDirection.RIGHT_TO_LEFT to stringResource(R.string.reader_direction_rtl),
                        ),
                        current = readingDirection,
                        onChange = onReadingDirectionChange,
                    )
                    OptionRow(
                        title = stringResource(R.string.reader_scale_title),
                        options = listOf(
                            ScaleType.SCREEN to stringResource(R.string.reader_scale_screen),
                            ScaleType.FIT_WIDTH to stringResource(R.string.reader_scale_fit_width),
                            ScaleType.FIT_HEIGHT to stringResource(R.string.reader_scale_fit_height),
                            ScaleType.ORIGINAL to stringResource(R.string.reader_scale_original),
                        ),
                        current = scaleType,
                        onChange = onScaleTypeChange,
                    )
                }

                ReaderType.CONTINUOUS -> {
                    OptionRow(
                        title = stringResource(R.string.reader_side_padding_title),
                        options = PADDING_STEPS.map { it to stringResource(R.string.reader_percent_value, (it * 100).toInt()) },
                        current = PADDING_STEPS.minBy { kotlin.math.abs(it - continuousPadding) },
                        onChange = onContinuousPaddingChange,
                        wrap = false,
                    )
                    OptionRow(
                        title = stringResource(R.string.reader_page_spacing_title),
                        options = SPACING_STEPS.map { it to stringResource(R.string.reader_dp_value, it) },
                        current = SPACING_STEPS.minBy { kotlin.math.abs(it - continuousPageSpacing) },
                        onChange = onContinuousPageSpacingChange,
                        wrap = false,
                    )
                }
            }

            OnOffRow(
                title = stringResource(R.string.reader_stretch_title),
                value = stretchToFit,
                onChange = onStretchToFitChange,
            )

            Text(
                text = stringResource(R.string.reader_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }
    }
}

/** 좌우 여백 단계: 0%~45%, 5% 단위 */
private val PADDING_STEPS = (0..9).map { it * 0.05f }

/** 쪽 간격 단계: 0~64dp, 8 단위 */
private val SPACING_STEPS = (0..8).map { it * 8 }

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
    SettingSelectorRow(
        title = title,
        currentValueText = options[index].second,
        onPrevious = { step(-1) },
        onNext = { step(1) },
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun OnOffRow(
    title: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingSelectorRow(
        title = title,
        currentValueText = stringResource(if (value) R.string.reader_option_on else R.string.reader_option_off),
        onPrevious = { onChange(!value) },
        onNext = { onChange(!value) },
        enabled = enabled,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingSelectorRow(
    title: String,
    currentValueText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        isFocused -> MaterialTheme.colorScheme.border
        else -> Color.Transparent
    }
    val backgroundColor = when {
        isFocused -> Color(0xFF2C2C2C)
        else -> Color(0xFF242424)
    }
    val alpha = if (enabled) 1f else 0.4f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor.copy(alpha = alpha))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                if (!enabled || keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        onPrevious()
                        true
                    }
                    Key.DirectionRight, Key.DirectionCenter, Key.Enter -> {
                        onNext()
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray.copy(alpha = alpha),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentValueText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = alpha),
                )
                if (enabled) {
                    Text(
                        text = "‹  ›",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isFocused) Color.White else Color.Gray,
                    )
                }
            }
        }
    }
}
