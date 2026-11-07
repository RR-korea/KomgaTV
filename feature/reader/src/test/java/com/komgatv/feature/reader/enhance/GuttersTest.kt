package com.komgatv.feature.reader.enhance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuttersTest {

    private val white = -1
    private val black = 0xFF000000.toInt()
    private val w = 100
    private val h = 400

    /** 테두리 2px짜리 칸: 가로 10~89, 세로 [top]부터 100줄 */
    private fun IntArray.panel(top: Int) {
        for (y in top until top + 100) for (x in 10 until 90) {
            val edge = y < top + 2 || y >= top + 98 || x < 12 || x >= 88
            if (edge) this[y * w + x] = black
        }
    }

    @Test
    fun `칸 사이 여백을 얇게 줄이고 가운데로 모으며, 여백의 글자는 무시하고 지운다`() {
        val px = IntArray(w * h) { white }
        px.panel(40)
        px.panel(200)
        // 칸 사이(140~199)의 작은 글자 — 획 높이 10줄이라 칸으로 치지 않는다
        for (y in 165 until 175) for (x in 30 until 60) px[y * w + x] = black

        val out = requireNotNull(collapseGutters(px, w, h))

        // 칸 100줄 + 남긴 여백 1줄 + 칸 100줄 = 201줄, 위 99줄·아래 100줄을 비운다
        val blackRows = (0 until h).filter { out[it * w + 45] == black }
        assertEquals(listOf(99, 100, 197, 198, 200, 201, 298, 299), blackRows)
        // 좌우는 그대로
        assertEquals(black, out[150 * w + 10])
        assertEquals(white, out[150 * w + 9])
    }

    @Test
    fun `쪽 끝의 스캔 테두리 줄은 칸으로 치지 않는다`() {
        val px = IntArray(w * h) { white }
        px.panel(40)
        px.panel(200)
        // 오른쪽 끝 위아래로 쭉 이어진 검은 줄 (스캔 테두리)
        for (y in 0 until h) px[y * w + w - 1] = black

        val out = requireNotNull(collapseGutters(px, w, h))
        val blackRows = (0 until h).filter { out[it * w + 45] == black }
        assertEquals(listOf(99, 100, 197, 198, 200, 201, 298, 299), blackRows)
    }

    @Test
    fun `지울 여백이 없으면 그대로`() {
        // 쪽 전체가 그림(세로 줄무늬)
        val px = IntArray(w * h) { i -> if (i % w % 10 < 3) black else white }
        assertNull(collapseGutters(px, w, h))
    }
}
