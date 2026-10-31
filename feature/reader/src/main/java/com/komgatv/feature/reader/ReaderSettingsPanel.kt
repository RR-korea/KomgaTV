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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType

/**
 * 리더 보기 설정 패널
 *
 * D-pad 전용 인터랙션을 제공합니다:
 * - 상/하: 설정 항목 간 포커스 이동
 * - 좌/우 또는 Center/Enter: 설정 값 순환 변경
 * - 열릴 때 첫 항목에 자동 포커스
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ReaderSettingsPanel(
    pageLayout: PageLayout,
    layoutOffset: Boolean,
    readingDirection: ReadingDirection,
    scaleType: ScaleType,
    stretchToFit: Boolean,
    onPageLayoutChange: (PageLayout) -> Unit,
    onLayoutOffsetChange: (Boolean) -> Unit,
    onReadingDirectionChange: (ReadingDirection) -> Unit,
    onScaleTypeChange: (ScaleType) -> Unit,
    onStretchToFitChange: (Boolean) -> Unit,
    onClose: () -> Unit,
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
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 1. 쪽 배치
            val layoutOptions = listOf(
                PageLayout.SINGLE_PAGE to stringResource(R.string.reader_layout_single),
                PageLayout.DOUBLE_PAGES to stringResource(R.string.reader_layout_double),
                PageLayout.DOUBLE_PAGES_NO_COVER to stringResource(R.string.reader_layout_double_no_cover),
            )
            SettingSelectorRow(
                title = stringResource(R.string.reader_layout_title),
                currentValueText = layoutOptions.firstOrNull { it.first == pageLayout }?.second.orEmpty(),
                onPrevious = {
                    val currentIndex = layoutOptions.indexOfFirst { it.first == pageLayout }
                    val prevIndex = if (currentIndex > 0) currentIndex - 1 else layoutOptions.lastIndex
                    onPageLayoutChange(layoutOptions[prevIndex].first)
                },
                onNext = {
                    val currentIndex = layoutOptions.indexOfFirst { it.first == pageLayout }
                    val nextIndex = (currentIndex + 1) % layoutOptions.size
                    onPageLayoutChange(layoutOptions[nextIndex].first)
                },
                modifier = Modifier.focusRequester(firstFocusRequester),
            )

            // 2. 쪽 맞춤 밀기 (offset) - 두 쪽 보기일 때만 유효
            val isDoublePage = pageLayout != PageLayout.SINGLE_PAGE
            SettingSelectorRow(
                title = stringResource(R.string.reader_offset_title),
                currentValueText = stringResource(if (layoutOffset) R.string.reader_option_on else R.string.reader_option_off),
                enabled = isDoublePage,
                onPrevious = { onLayoutOffsetChange(!layoutOffset) },
                onNext = { onLayoutOffsetChange(!layoutOffset) },
            )

            // 3. 읽기 방향
            val directionOptions = listOf(
                ReadingDirection.LEFT_TO_RIGHT to stringResource(R.string.reader_direction_ltr),
                ReadingDirection.RIGHT_TO_LEFT to stringResource(R.string.reader_direction_rtl),
            )
            SettingSelectorRow(
                title = stringResource(R.string.reader_direction_title),
                currentValueText = directionOptions.firstOrNull { it.first == readingDirection }?.second.orEmpty(),
                onPrevious = {
                    val currentIndex = directionOptions.indexOfFirst { it.first == readingDirection }
                    val prevIndex = if (currentIndex > 0) currentIndex - 1 else directionOptions.lastIndex
                    onReadingDirectionChange(directionOptions[prevIndex].first)
                },
                onNext = {
                    val currentIndex = directionOptions.indexOfFirst { it.first == readingDirection }
                    val nextIndex = (currentIndex + 1) % directionOptions.size
                    onReadingDirectionChange(directionOptions[nextIndex].first)
                },
            )

            // 4. 크기 맞춤
            val scaleOptions = listOf(
                ScaleType.SCREEN to stringResource(R.string.reader_scale_screen),
                ScaleType.FIT_WIDTH to stringResource(R.string.reader_scale_fit_width),
                ScaleType.FIT_HEIGHT to stringResource(R.string.reader_scale_fit_height),
                ScaleType.ORIGINAL to stringResource(R.string.reader_scale_original),
            )
            SettingSelectorRow(
                title = stringResource(R.string.reader_scale_title),
                currentValueText = scaleOptions.firstOrNull { it.first == scaleType }?.second.orEmpty(),
                onPrevious = {
                    val currentIndex = scaleOptions.indexOfFirst { it.first == scaleType }
                    val prevIndex = if (currentIndex > 0) currentIndex - 1 else scaleOptions.lastIndex
                    onScaleTypeChange(scaleOptions[prevIndex].first)
                },
                onNext = {
                    val currentIndex = scaleOptions.indexOfFirst { it.first == scaleType }
                    val nextIndex = (currentIndex + 1) % scaleOptions.size
                    onScaleTypeChange(scaleOptions[nextIndex].first)
                },
            )

            // 5. 작은 이미지 키우기
            SettingSelectorRow(
                title = stringResource(R.string.reader_stretch_title),
                currentValueText = stringResource(if (stretchToFit) R.string.reader_option_on else R.string.reader_option_off),
                onPrevious = { onStretchToFitChange(!stretchToFit) },
                onNext = { onStretchToFitChange(!stretchToFit) },
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.reader_close_settings))
            }
        }
    }
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
