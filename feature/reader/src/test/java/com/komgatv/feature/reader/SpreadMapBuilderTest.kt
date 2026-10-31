package com.komgatv.feature.reader

import com.komgatv.core.model.BookPage
import com.komgatv.core.model.PageLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SpreadMapBuilder 단위 테스트
 *
 * 지시서 요구사항 검증:
 * - 펼침 지도 (표지 있음/없음/밀기/가로 쪽 단독/홀수 끝)
 */
class SpreadMapBuilderTest {

    private fun createPage(number: Int, isLandscape: Boolean = false): BookPage {
        val (w, h) = if (isLandscape) 1600 to 1200 else 1000 to 1400
        return BookPage(number = number, width = w, height = h)
    }

    private fun createPages(count: Int, landscapePages: Set<Int> = emptySet()): List<BookPage> {
        return (1..count).map { pageNum ->
            createPage(pageNum, isLandscape = pageNum in landscapePages)
        }
    }

    @Test
    fun `SINGLE_PAGE - 모든 페이지가 1장씩 단독 펼침으로 생성된다`() {
        val pages = createPages(5)
        val spreads = SpreadMapBuilder.buildSpreadMap(pages, PageLayout.SINGLE_PAGE)

        assertEquals(5, spreads.size)
        spreads.forEachIndexed { index, spread ->
            assertEquals(1, spread.pages.size)
            assertEquals(index + 1, spread.pages.first().number)
        }
    }

    @Test
    fun `DOUBLE_PAGES - 표지는 단독이며 이후 페이지는 2쪽씩 묶인다`() {
        // 1..5: [1], [2, 3], [4, 5]
        val pages = createPages(5)
        val spreads = SpreadMapBuilder.buildSpreadMap(pages, PageLayout.DOUBLE_PAGES, offset = false)

        assertEquals(3, spreads.size)
        assertEquals(listOf(1), spreads[0].pages.map { it.number })
        assertEquals(listOf(2, 3), spreads[1].pages.map { it.number })
        assertEquals(listOf(4, 5), spreads[2].pages.map { it.number })
    }

    @Test
    fun `DOUBLE_PAGES_NO_COVER - 첫 페이지부터 2쪽씩 묶인다`() {
        // 1..6: [1, 2], [3, 4], [5, 6]
        val pages = createPages(6)
        val spreads = SpreadMapBuilder.buildSpreadMap(pages, PageLayout.DOUBLE_PAGES_NO_COVER, offset = false)

        assertEquals(3, spreads.size)
        assertEquals(listOf(1, 2), spreads[0].pages.map { it.number })
        assertEquals(listOf(3, 4), spreads[1].pages.map { it.number })
        assertEquals(listOf(5, 6), spreads[2].pages.map { it.number })
    }

    @Test
    fun `DOUBLE_PAGES + offset - 표지 단독 후 1쪽을 밀어서 짝을 맞춘다`() {
        // 1..6: [1], [2], [3, 4], [5, 6]
        val pages = createPages(6)
        val spreads = SpreadMapBuilder.buildSpreadMap(pages, PageLayout.DOUBLE_PAGES, offset = true)

        assertEquals(4, spreads.size)
        assertEquals(listOf(1), spreads[0].pages.map { it.number })
        assertEquals(listOf(2), spreads[1].pages.map { it.number })
        assertEquals(listOf(3, 4), spreads[2].pages.map { it.number })
        assertEquals(listOf(5, 6), spreads[3].pages.map { it.number })
    }

    @Test
    fun `DOUBLE_PAGES_NO_COVER + offset - 첫 페이지가 1쪽 밀려 단독이 되고 이후 2쪽씩 묶인다`() {
        // 1..6: [1], [2, 3], [4, 5], [6]
        val pages = createPages(6)
        val spreads = SpreadMapBuilder.buildSpreadMap(pages, PageLayout.DOUBLE_PAGES_NO_COVER, offset = true)

        assertEquals(4, spreads.size)
        assertEquals(listOf(1), spreads[0].pages.map { it.number })
        assertEquals(listOf(2, 3), spreads[1].pages.map { it.number })
        assertEquals(listOf(4, 5), spreads[2].pages.map { it.number })
        assertEquals(listOf(6), spreads[3].pages.map { it.number })
    }

    @Test
    fun `가로 쪽 단독 - 가로가 긴 펼침 스캔 페이지는 단독으로 표시된다`() {
        // 1(표지), 2(세로), 3(가로), 4(세로), 5(세로)
        // 세그먼트 1 (표지): [1]
        // 세그먼트 2 (가로 3 전): singlePages=[2], landscapePage=3
        // 세그먼트 3 (남은 페이지): singlePages=[4, 5], landscapePage=null
        // 결과: [1], [2], [3], [4, 5]
        val pages = createPages(5, landscapePages = setOf(3))
        val spreads = SpreadMapBuilder.buildSpreadMap(pages, PageLayout.DOUBLE_PAGES, offset = false)

        assertEquals(4, spreads.size)
        assertEquals(listOf(1), spreads[0].pages.map { it.number })
        assertEquals(listOf(2), spreads[1].pages.map { it.number })
        assertEquals(listOf(3), spreads[2].pages.map { it.number })
        assertEquals(listOf(4, 5), spreads[3].pages.map { it.number })
    }

    @Test
    fun `홀수 끝 - 두 쪽 보기에서 페이지 수가 홀수로 끝나면 마지막 페이지는 단독으로 묶인다`() {
        // DOUBLE_PAGES_NO_COVER, 1..5: [1, 2], [3, 4], [5]
        val pages = createPages(5)
        val spreads = SpreadMapBuilder.buildSpreadMap(pages, PageLayout.DOUBLE_PAGES_NO_COVER, offset = false)

        assertEquals(3, spreads.size)
        assertEquals(listOf(1, 2), spreads[0].pages.map { it.number })
        assertEquals(listOf(3, 4), spreads[1].pages.map { it.number })
        assertEquals(listOf(5), spreads[2].pages.map { it.number })

        // DOUBLE_PAGES, 1..6 (표지 제외 5장): [1], [2, 3], [4, 5], [6]
        val pages6 = createPages(6)
        val spreads6 = SpreadMapBuilder.buildSpreadMap(pages6, PageLayout.DOUBLE_PAGES, offset = false)

        assertEquals(4, spreads6.size)
        assertEquals(listOf(1), spreads6[0].pages.map { it.number })
        assertEquals(listOf(2, 3), spreads6[1].pages.map { it.number })
        assertEquals(listOf(4, 5), spreads6[2].pages.map { it.number })
        assertEquals(listOf(6), spreads6[3].pages.map { it.number })
    }
}
