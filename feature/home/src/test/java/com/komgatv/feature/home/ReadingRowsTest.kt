package com.komgatv.feature.home

import com.komgatv.core.model.Book
import com.komgatv.core.model.BookMetadata
import com.komgatv.core.model.ReadProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingRowsTest {

    private fun book(id: String, series: String, readAt: Long? = null) = Book(
        id = id, seriesId = series, seriesTitle = series, name = id, number = 1, pageCount = 10,
        metadata = BookMetadata(id, "", "1"),
        readProgress = readAt?.let { ReadProgress(page = 3, completed = false, readDateMillis = it) },
    )

    @Test
    fun `뺀 시리즈는 읽는 중과 다음 권 모두에서 빠진다`() {
        val rows = filterReadingRows(
            continueReading = listOf(book("k9", "kingdom", readAt = 100), book("b1", "bleach", readAt = 100)),
            onDeck = listOf(book("k10", "kingdom"), book("o2", "onepiece")),
            hidden = mapOf("kingdom" to 200L),
        )
        assertEquals(listOf("b1"), rows.continueReading.map { it.id })
        assertEquals(listOf("o2"), rows.onDeck.map { it.id })
        assertEquals(emptySet<String>(), rows.reactivated)
    }

    @Test
    fun `뺀 뒤에 다시 읽으면 다시 보이고 다시 읽은 시리즈로 알린다`() {
        val rows = filterReadingRows(
            continueReading = listOf(book("k9", "kingdom", readAt = 300)),
            onDeck = listOf(book("k10", "kingdom")),
            hidden = mapOf("kingdom" to 200L),
        )
        assertEquals(listOf("k9"), rows.continueReading.map { it.id })
        assertEquals(listOf("k10"), rows.onDeck.map { it.id })
        assertEquals(setOf("kingdom"), rows.reactivated)
    }
}
