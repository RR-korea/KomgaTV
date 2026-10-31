package com.komgatv.core.model

/**
 * Komga 시리즈 도메인 모델
 *
 * 만화/웹툰 시리즈를 나타내며 해당 시리즈에 포함된 책(권) 정보를 포함합니다.
 *
 * @property id 시리즈 고유 식별자
 * @property libraryId 소속 라이브러리 ID
 * @property name 시리즈 이름
 * @property booksCount 시리즈에 포함된 총 책 수
 * @property metadata 시리즈 메타데이터
 */
data class Series(
    val id: String,
    val libraryId: String,
    val name: String,
    val booksCount: Int,
    val metadata: SeriesMetadata,
)

/**
 * 시리즈 메타데이터
 *
 * 시리즈에 대한 부가 정보를 담고 있습니다.
 *
 * @property status 시리즈 상태 (연재 중, 완결 등)
 * @property title 메타데이터에서 가져온 제목
 * @property summary 시리즈 요약/줄거리
 * @property publisher 출판사
 * @property genres 장르 목록
 * @property tags 태그 목록
 * @property totalBookCount 전체 권수 (메타데이터 기준)
 * @property ageRating 연령 등급
 * @property language 언어 코드 (ISO 639)
 */
data class SeriesMetadata(
    val status: String = "",
    val title: String = "",
    val summary: String = "",
    val publisher: String = "",
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val totalBookCount: Int = 0,
    val ageRating: Int? = null,
    val language: String = "",
)
