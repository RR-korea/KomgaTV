package com.komgatv.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Komga API 페이지네이션 응답 래퍼
 *
 * Spring Data의 Page 응답 형식에 대응하는 제네릭 페이지 응답 모델입니다.
 *
 * @param T 콘텐츠 항목의 타입
 * @property content 현재 페이지의 항목 목록
 * @property pageable 페이지 정보
 * @property totalElements 전체 항목 수
 * @property totalPages 전체 페이지 수
 * @property size 페이지 크기
 * @property number 현재 페이지 번호 (0-based)
 * @property numberOfElements 현재 페이지의 항목 수
 * @property first 첫 번째 페이지 여부
 * @property last 마지막 페이지 여부
 * @property empty 빈 페이지 여부
 */
@Serializable
data class PagedResponse<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0L,
    val totalPages: Int = 0,
    val size: Int = 20,
    val number: Int = 0,
    val numberOfElements: Int = 0,
    val first: Boolean = true,
    val last: Boolean = true,
    val empty: Boolean = true,
)
