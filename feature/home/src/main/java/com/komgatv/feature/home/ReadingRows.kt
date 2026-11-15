package com.komgatv.feature.home

import com.komgatv.core.model.Book

/**
 * "읽는 중"에서 뺀 시리즈를 거른 결과
 *
 * @property reactivated 뺀 뒤에 다시 읽은 시리즈 — 다시 보이고 뺀 기록을 지운다
 */
internal data class ReadingRows(
    val continueReading: List<Book>,
    val reactivated: Set<String>,
)

/**
 * 뺀 시리즈([hidden]: 시리즈 ID → 뺀 시각 ms)를 "읽는 중" 줄에서 거른다.
 * 그 시리즈의 책을 뺀 시각 뒤에 읽었으면(서버의 readDate) 다시 읽기 시작한 것으로 보고 다시 보인다.
 */
internal fun filterReadingRows(continueReading: List<Book>, hidden: Map<String, Long>): ReadingRows {
    if (hidden.isEmpty()) return ReadingRows(continueReading, emptySet())
    val reactivated = continueReading
        .filter { book ->
            val hiddenAt = hidden[book.seriesId] ?: return@filter false
            (book.readProgress?.readDateMillis ?: 0L) > hiddenAt
        }
        .map { it.seriesId }
        .toSet()
    val stillHidden = hidden.keys - reactivated
    return ReadingRows(
        continueReading = continueReading.filter { it.seriesId !in stillHidden },
        reactivated = reactivated,
    )
}
