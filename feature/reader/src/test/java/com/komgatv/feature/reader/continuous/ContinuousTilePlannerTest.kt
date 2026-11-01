package com.komgatv.feature.reader.continuous

import com.komgatv.core.model.BookPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousTilePlannerTest {

    private val defaultParams = ContinuousTilePlanner.PlanParams(
        screenWidthPx = 1920,
        screenHeightPx = 1080,
        sidePaddingFraction = 0f,
        pageSpacingDp = 0,
    )

    @Test
    fun plan_small_page_creates_single_tile() {
        // 너비에 맞춰 키워도(×1.92) 화면 높이(1080) 이하인 쪽 (1000x500 → 1920x960)
        val page = BookPage(number = 1, width = 1000, height = 500)
        val tiles = ContinuousTilePlanner.planPageTiles(page, defaultParams)

        assertEquals(1, tiles.size)
        val tile = tiles.first()
        assertEquals(1, tile.pageNumber)
        assertEquals(0, tile.tileIndex)
        assertEquals(1, tile.totalTilesInPage)
        assertFalse(tile.isPlaceholder)
        assertEquals(TileRegion(0, 0, 1000, 500), tile.region)
        assertEquals(1920, tile.displayWidth) // 너비 맞춤 (1000 -> 1920, scale = 1.92)
        assertEquals(960, tile.displayHeight)
    }

    @Test
    fun plan_tall_page_splits_into_multiple_tiles() {
        // 화면보다 긴 쪽: 웹툰 세로 긴 페이지 (1080x10000, 화면은 1920x1080)
        // scale = 1920 / 1080 = 1.777...
        // maxSrcTileHeight = 1080 / 1.777... = 607px
        // 10000px / 607px ~= 17개 타일
        val page = BookPage(number = 2, width = 1080, height = 10000)
        val tiles = ContinuousTilePlanner.planPageTiles(page, defaultParams)

        assertTrue("Should split into multiple tiles", tiles.size > 1)
        assertEquals(tiles.size, tiles.first().totalTilesInPage)

        // 모든 타일의 그려질 높이가 화면 높이(1080) 이하인지 확인
        tiles.forEach { tile ->
            assertTrue("Tile height ${tile.displayHeight} must be <= screenHeight", tile.displayHeight <= 1080)
        }

        // 타일 조각들이 위에서부터 아래로 빈틈없이 연속되는지 확인
        var expectedTop = 0
        tiles.forEachIndexed { index, tile ->
            assertEquals(index, tile.tileIndex)
            val reg = tile.region!!
            assertEquals(expectedTop, reg.top)
            assertEquals(0, reg.left)
            assertEquals(1080, reg.right)
            expectedTop = reg.bottom
        }
        assertEquals(10000, expectedTop)
    }

    @Test
    fun plan_null_size_page_creates_placeholder_tile() {
        // 크기 null: 너비, 높이가 아직 없는 페이지
        val page = BookPage(number = 3, width = null, height = null)
        val tiles = ContinuousTilePlanner.planPageTiles(page, defaultParams)

        assertEquals(1, tiles.size)
        val tile = tiles.first()
        assertEquals(3, tile.pageNumber)
        assertEquals(0, tile.tileIndex)
        assertEquals(1, tile.totalTilesInPage)
        assertTrue(tile.isPlaceholder)
        assertNull(tile.region)
        assertEquals(1920, tile.displayWidth)
        assertEquals(1080, tile.displayHeight)
    }

    @Test
    fun plan_applies_side_padding_and_page_spacing() {
        // 여백 10% (0.1f), 쪽 간격 16dp
        val params = defaultParams.copy(
            sidePaddingFraction = 0.10f,
            pageSpacingDp = 16,
        )
        // 가용 폭 = 1920 * (1 - 0.2) = 1536px
        val page = BookPage(number = 4, width = 1000, height = 3000)
        val tiles = ContinuousTilePlanner.planPageTiles(page, params)

        assertTrue(tiles.size > 1)
        tiles.forEach { tile ->
            assertEquals(1536, tile.displayWidth)
        }

        // 마지막 타일에만 쪽 간격(16dp)이 적용되고 앞 타일들은 0이어야 함
        tiles.dropLast(1).forEach { tile ->
            assertEquals(0, tile.bottomSpacingDp)
        }
        assertEquals(16, tiles.last().bottomSpacingDp)
    }

    @Test
    fun plan_small_page_is_always_stretched_to_width() {
        // 원본 너비 800 < 화면 1920 — 늘 화면 너비로 키운다 (높이 1440 > 화면 1080이라 조각 2개)
        val page = BookPage(number = 5, width = 800, height = 600)
        val tiles = ContinuousTilePlanner.planPageTiles(page, defaultParams)

        assertTrue(tiles.all { it.displayWidth == 1920 })
        // 조각마다 반올림해 1px쯤 어긋날 수 있다
        assertEquals(1440f, tiles.sumOf { it.displayHeight }.toFloat(), 2f)
    }
}
