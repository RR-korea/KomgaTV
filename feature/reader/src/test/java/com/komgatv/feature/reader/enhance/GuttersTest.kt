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

        // 칸 100줄 + 칸 100줄 = 200줄이 붙고, 위아래 100줄씩 비운다
        val blackRows = (0 until h).filter { out[it * w + 45] == black }
        assertEquals(listOf(100, 101, 198, 199, 200, 201, 298, 299), blackRows)
        // 좌우는 그대로
        assertEquals(black, out[150 * w + 10])
        assertEquals(white, out[150 * w + 9])
    }

    /** 테두리 2px짜리 칸: 가로 [x0]~[x1] 앞, 세로 [y0]~[y1] 앞 */
    private fun IntArray.box(x0: Int, x1: Int, y0: Int, y1: Int) {
        for (y in y0 until y1) for (x in x0 until x1) {
            val edge = y < y0 + 2 || y >= y1 - 2 || x < x0 + 2 || x >= x1 - 2
            if (edge) this[y * w + x] = black
        }
    }

    @Test
    fun `옆에 긴 칸이 있는 작은 칸들도 여백을 붙여 쪽 가운데 쪽으로 민다`() {
        val px = IntArray(w * h) { white }
        // 오른쪽 세로로 긴 칸, 왼쪽에 작은 칸 둘 (사이 40줄) — 쪽 위쪽 절반
        px.box(55, 95, 20, 180)
        px.box(6, 50, 20, 80)
        px.box(6, 50, 120, 180)

        val out = requireNotNull(collapseGutters(px, w, h))

        // 띠 160줄이 가운데(120~279)로. 긴 칸은 그대로
        assertEquals(listOf(120, 121, 278, 279), (0 until h).filter { out[it * w + 70] == black })
        // 작은 칸 둘은 맞붙고, 위쪽 띠라 아래(쪽 가운데 쪽)로 붙는다
        assertEquals(listOf(160, 161, 218, 219, 220, 221, 278, 279), (0 until h).filter { out[it * w + 20] == black })
    }

    @Test
    fun `칸 사이에 걸친 글 상자는 여백으로 보고, 상자는 빈 안쪽만 짧아지며 테두리가 이어진다`() {
        val px = IntArray(w * h) { white }
        px.panel(40)
        px.panel(200)
        // 칸 A 아래쪽부터 칸 B 중간까지 걸친 좁은 글 상자 (안쪽은 비어 있다)
        px.box(60, 80, 100, 250)

        val out = requireNotNull(collapseGutters(px, w, h))

        // 칸은 첫 시험과 같은 자리로 붙는다
        assertEquals(listOf(100, 101, 198, 199, 200, 201, 298, 299), (0 until h).filter { out[it * w + 45] == black })
        // 상자 왼쪽 테두리는 끊기지 않고 160~249로 이어진다 (칸 A 위 테두리 100·101, 칸 B 아래 테두리 298·299)
        val boxRows = (0 until h).filter { out[it * w + 60] == black }
        assertEquals(listOf(100, 101) + (160..249).toList() + listOf(298, 299), boxRows)
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
        assertEquals(listOf(100, 101, 198, 199, 200, 201, 298, 299), blackRows)
    }

    @Test
    fun `검은 칸 틈도 붙인다`() {
        val px = IntArray(w * h) { white }
        px.panel(40)
        // 칸 사이(140~159)가 쪽 끝까지 검정
        for (y in 140 until 160) for (x in 0 until w) px[y * w + x] = black
        px.panel(160)

        val out = requireNotNull(collapseGutters(px, w, h))

        // 칸 둘(200줄)이 맞붙어 가운데(100~299)로, 검은 틈은 없어진다
        assertEquals(listOf(100, 101, 198, 199, 200, 201, 298, 299), (0 until h).filter { out[it * w + 45] == black })
        assertEquals(emptyList<Int>(), (0 until h).filter { out[it * w + 5] == black })
    }

    @Test
    fun `그림 속 넓은 검은 곳은 틈으로 보지 않는다`() {
        val px = IntArray(w * h) { white }
        px.panel(40)
        // 쪽 높이의 6%보다 긴(60줄) 검은 띠 — 그림 속 검은 하늘 같은 것
        for (y in 140 until 200) for (x in 0 until w) px[y * w + x] = black
        px.panel(200)

        val out = requireNotNull(collapseGutters(px, w, h))
        // 칸과 검은 띠가 한 덩어리(260줄)로 남아 가운데로 모일 뿐 — 검은 띠 60줄이 그대로 있다
        assertEquals(60, (0 until h).count { out[it * w + 5] == black })
    }

    @Test
    fun `지울 여백이 없으면 그대로`() {
        // 쪽 전체가 그림(세로 줄무늬)
        val px = IntArray(w * h) { i -> if (i % w % 10 < 3) black else white }
        assertNull(collapseGutters(px, w, h))
    }
}
