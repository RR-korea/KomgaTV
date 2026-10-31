package com.komgatv.feature.reader.continuous

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousProgressHelperTest {

    @Test
    fun calculateProgress_returns_highest_visible_page() {
        val visibleTiles = listOf(
            PageTile(pageNumber = 10, tileIndex = 1, totalTilesInPage = 2, region = null, displayWidth = 100, displayHeight = 100, bottomSpacingDp = 0),
            PageTile(pageNumber = 11, tileIndex = 0, totalTilesInPage = 3, region = null, displayWidth = 100, displayHeight = 100, bottomSpacingDp = 0),
            PageTile(pageNumber = 11, tileIndex = 1, totalTilesInPage = 3, region = null, displayWidth = 100, displayHeight = 100, bottomSpacingDp = 0),
        )

        val result = ContinuousProgressHelper.calculateProgress(visibleTiles, totalPages = 50)
        assertEquals(11, result?.pageNumber)
        assertFalse(result?.completed ?: true)
    }

    @Test
    fun calculateProgress_marks_completed_only_when_last_tile_of_last_page_is_visible() {
        // 마지막 페이지(50)의 중간 타일(0/2)만 보이는 경우 -> completed = false
        val partialLastPage = listOf(
            PageTile(pageNumber = 50, tileIndex = 0, totalTilesInPage = 2, region = null, displayWidth = 100, displayHeight = 100, bottomSpacingDp = 0),
        )
        val partialResult = ContinuousProgressHelper.calculateProgress(partialLastPage, totalPages = 50)
        assertEquals(50, partialResult?.pageNumber)
        assertFalse(partialResult?.completed ?: true)

        // 마지막 페이지(50)의 마지막 타일(1/2)이 보이는 경우 -> completed = true
        val fullLastPage = listOf(
            PageTile(pageNumber = 50, tileIndex = 0, totalTilesInPage = 2, region = null, displayWidth = 100, displayHeight = 100, bottomSpacingDp = 0),
            PageTile(pageNumber = 50, tileIndex = 1, totalTilesInPage = 2, region = null, displayWidth = 100, displayHeight = 100, bottomSpacingDp = 0),
        )
        val completedResult = ContinuousProgressHelper.calculateProgress(fullLastPage, totalPages = 50)
        assertEquals(50, completedResult?.pageNumber)
        assertTrue(completedResult?.completed ?: false)
    }

    @Test
    fun calculateProgress_returns_null_when_no_tiles_visible() {
        val result = ContinuousProgressHelper.calculateProgress(emptyList(), totalPages = 50)
        assertNull(result)
    }
}
