package com.komgatv.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * TV에 최적화된 수평 스크롤 행 컴포저블
 *
 * 제목 라벨과 함께 아이템을 수평으로 스크롤 가능하게 표시합니다.
 * TvLazyRow를 사용하여 D-Pad 네비게이션과 포커스 복원을 지원합니다.
 *
 * @param T 아이템 타입
 * @param title 행 상단에 표시될 제목
 * @param items 표시할 아이템 목록
 * @param modifier Modifier
 * @param itemContent 각 아이템의 컴포저블 콘텐츠
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun <T> TVRow(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    // 포커스 복원을 위한 리스트 상태 유지
    val listState = rememberTvLazyListState()

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

        // 수평 스크롤 아이템 목록
        TvLazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = items,
                key = { item -> item.hashCode() }, // 안정적인 키로 포커스 복원 지원
            ) { item ->
                itemContent(item)
            }
        }
    }
}

/**
 * 키 기반 TVRow 오버로드
 *
 * 아이템에 고유 키를 제공하여 포커스 복원을 더 안정적으로 지원합니다.
 *
 * @param T 아이템 타입
 * @param title 행 제목
 * @param items 아이템 목록
 * @param key 각 아이템의 고유 키를 반환하는 함수
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
    val listState = rememberTvLazyListState()

    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = 48.dp,
                bottom = 12.dp,
            ),
        )

        TvLazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
