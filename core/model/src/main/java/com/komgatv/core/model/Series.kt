package com.komgatv.core.model

/** Komga 시리즈 */
data class Series(
    val id: String,
    val libraryId: String,
    val name: String,
    val booksCount: Int,
    val booksReadCount: Int,
    val booksInProgressCount: Int,
    val metadata: SeriesMetadata,
)

data class SeriesMetadata(
    val title: String,
    val summary: String,
    val status: String,
    val publisher: String,
    val genres: List<String>,
    /** 시리즈에 지정된 읽기 방향. 없으면 null (리더 기본값 사용) */
    val readingDirection: ReadingDirection?,
)

/** 읽기 방향 — Komga의 KomgaReadingDirection과 같은 값 */
enum class ReadingDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    VERTICAL,
    WEBTOON,
}
