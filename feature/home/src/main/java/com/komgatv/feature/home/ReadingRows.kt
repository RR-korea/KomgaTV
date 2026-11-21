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
 * "읽는 중" 줄 — 읽던 책([inProgress])과, 최근에 한 권을 끝까지 읽어 읽던 책이 없는 시리즈([recentlyRead]).
 * 그런 시리즈는 다음 권([onDeck])을, 다음 권이 아직 없으면(나와 있는 데까지 다 읽음 — 연재 중인 작품) 마지막으로 읽은 책을 넣는다.
 * 새 권이 서버에 들어오면 다음 권으로 바뀐다. 마지막으로 다 읽은 시각으로 줄을 선다 — 방금 다 읽은 시리즈가 맨 앞.
 * 그만 보고 싶은 시리즈는 "읽는 중에서 빼기"로 뺀다.
 */
internal fun mergeReadingRow(inProgress: List<Book>, recentlyRead: List<Book>, onDeck: List<Book>, size: Int): List<Book> {
    val readingSeries = inProgress.map { it.seriesId }.toSet()
    val nextBySeries = onDeck.associateBy { it.seriesId }
    val finished = recentlyRead
        .filter { it.seriesId !in readingSeries }
        .distinctBy { it.seriesId }
        .map { read -> (nextBySeries[read.seriesId] ?: read) to (read.readProgress?.readDateMillis ?: 0L) }
    return (inProgress.map { it to (it.readProgress?.readDateMillis ?: 0L) } + finished)
        .sortedByDescending { it.second }
        .map { it.first }
        .take(size)
}

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
