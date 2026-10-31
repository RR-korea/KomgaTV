package com.komgatv.core.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Komga 책(단권) 도메인 모델
 *
 * 시리즈 내의 개별 책(권)을 나타냅니다.
 * 만화 단행본 한 권 또는 챕터에 해당합니다.
 *
 * @property id 책 고유 식별자
 * @property seriesId 소속 시리즈 ID
 * @property name 책 이름 (파일명 기반)
 * @property number 시리즈 내 순번
 * @property sizeBytes 파일 크기 (바이트)
 * @property pageCount 총 페이지 수
 * @property metadata 책 메타데이터
 * @property readProgress 읽기 진행 상태 (null이면 읽지 않음)
 */
data class Book(
    val id: String,
    val seriesId: String,
    val name: String,
    val number: Int,
    val sizeBytes: Long,
    val pageCount: Int,
    val metadata: BookMetadata,
    val readProgress: ReadProgress? = null,
)

/**
 * 책 메타데이터
 *
 * @property title 책 제목
 * @property summary 책 요약
 * @property authors 저자 목록
 * @property releaseDate 출간일
 * @property isbn ISBN 코드
 */
data class BookMetadata(
    val title: String = "",
    val summary: String = "",
    val authors: List<String> = emptyList(),
    val releaseDate: LocalDate? = null,
    val isbn: String = "",
)

/**
 * 읽기 진행 상태
 *
 * 사용자의 책 읽기 진행 정보를 추적합니다.
 *
 * @property page 현재 읽고 있는 페이지 번호 (1-based)
 * @property completed 완독 여부
 * @property readDate 마지막으로 읽은 일시
 */
data class ReadProgress(
    val page: Int,
    val completed: Boolean,
    val readDate: LocalDateTime,
)
