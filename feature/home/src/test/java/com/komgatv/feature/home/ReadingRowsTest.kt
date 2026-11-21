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
    fun `뺀 시리즈는 읽는 중에서 빠진다`() {
        val rows = filterReadingRows(
            continueReading = listOf(book("k9", "kingdom", readAt = 100), book("b1", "bleach", readAt = 100)),
            hidden = mapOf("kingdom" to 200L),
        )
        assertEquals(listOf("b1"), rows.continueReading.map { it.id })
        assertEquals(emptySet<String>(), rows.reactivated)
    }

    @Test
    fun `뺀 뒤에 다시 읽으면 다시 보이고 다시 읽은 시리즈로 알린다`() {
        val rows = filterReadingRows(
            continueReading = listOf(book("k9", "kingdom", readAt = 300)),
            hidden = mapOf("kingdom" to 200L),
        )
        assertEquals(listOf("k9"), rows.continueReading.map { it.id })
        assertEquals(setOf("kingdom"), rows.reactivated)
    }

    @Test
    fun `한 권을 끝까지 읽은 시리즈는 다음 권으로 읽는 중에 남고 다 읽은 시각으로 줄을 선다`() {
        val row = mergeReadingRow(
            inProgress = listOf(book("b3", "bleach", readAt = 100)),
            // 킹덤 74권을 방금(300) 다 읽었다, 73권은 그 전에
            recentlyRead = listOf(book("k74", "kingdom", readAt = 300), book("k73", "kingdom", readAt = 50)),
            onDeck = listOf(book("k75", "kingdom")),
            size = 20,
        )
        assertEquals(listOf("k75", "b3"), row.map { it.id })
    }

    @Test
    fun `읽던 책이 있는 시리즈는 다 읽은 권을 따로 넣지 않는다`() {
        val row = mergeReadingRow(
            inProgress = listOf(book("k76", "kingdom", readAt = 100)),
            recentlyRead = listOf(book("k75", "kingdom", readAt = 90)),
            onDeck = emptyList(),
            size = 20,
        )
        assertEquals(listOf("k76"), row.map { it.id })
    }

    @Test
    fun `나와 있는 데까지 다 읽은 시리즈는 마지막으로 읽은 책으로 남는다 - 새 권이 나오면 다음 권으로`() {
        val row = mergeReadingRow(
            inProgress = listOf(book("b3", "bleach", readAt = 100)),
            recentlyRead = listOf(book("k884", "kingdom", readAt = 300), book("k883", "kingdom", readAt = 290)),
            onDeck = emptyList(),
            size = 20,
        )
        assertEquals(listOf("k884", "b3"), row.map { it.id })

        val later = mergeReadingRow(
            inProgress = emptyList(),
            recentlyRead = listOf(book("k884", "kingdom", readAt = 300)),
            onDeck = listOf(book("k885", "kingdom")),
            size = 20,
        )
        assertEquals(listOf("k885"), later.map { it.id })
    }
}
