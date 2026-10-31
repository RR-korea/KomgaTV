package com.komgatv.core.model

import java.time.LocalDateTime

/**
 * Komga 라이브러리 도메인 모델
 *
 * 서버에 등록된 만화/웹툰 라이브러리를 나타냅니다.
 * 각 라이브러리는 파일 시스템의 루트 디렉터리에 매핑됩니다.
 *
 * @property id 라이브러리 고유 식별자
 * @property name 라이브러리 표시 이름
 * @property root 라이브러리 루트 디렉터리 경로
 * @property createdDate 생성 일시
 * @property lastModifiedDate 마지막 수정 일시
 */
data class Library(
    val id: String,
    val name: String,
    val root: String,
    val createdDate: LocalDateTime,
    val lastModifiedDate: LocalDateTime,
)
