package com.komgatv.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * TV에 최적화된 수평 스크롤 행 컴포저블
 *
 * 제목 라벨과 함께 아이템을 수평으로 스크롤 가능하게 표시합니다.
 * 표준 LazyRow와 focusRestorer를 사용하여 D-Pad 네비게이션과 포커스 복원을 지원합니다.
 * 아이템의 안정적인 고유 키(id 등)를 필수로 전달받아 포커스 상태를 정확히 보존합니다.
 *
 * @param T 아이템 타입
 * @param title 행 상단에 표시될 제목
 * @param items 표시할 아이템 목록
 * @param key 각 아이템의 고유 키를 반환하는 함수 (안정적인 포커스 복원을 위해 필수)
 * @param modifier Modifier
 * @param itemContent 각 아이템의 컴포저블 콘텐츠
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun <T> TVRow(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    val listState = rememberLazyListState()

    Column(modifier = modifier.padding(vertical = 12.dp)) {
        // 섹션 제목
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = 48.dp, // TV UI 좌측 여백
                bottom = 12.dp,
            ),
        )

        // 수평 스크롤 아이템 목록 (D4: 표준 LazyRow + focusRestorer)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            items(
                items = items,
                key = key,
            ) { item ->
                itemContent(item)
            }
        }
    }
}
