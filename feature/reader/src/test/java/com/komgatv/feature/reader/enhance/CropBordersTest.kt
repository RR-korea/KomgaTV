package com.komgatv.feature.reader.enhance

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropBordersTest {

    private val white = -1
    private val black = 0xFF000000.toInt()
    private val gray = 0xFF808080.toInt()
    private val w = 200
    private val h = 300

    /** [x0, x1) × [y0, y1) 안에 빗금 그림 (흰·검 섞임) */
    private fun IntArray.art(x0: Int, x1: Int, y0: Int, y1: Int) {
        for (y in y0 until y1) for (x in x0 until x1) this[y * w + x] = if ((x + y) % 5 < 2) black else white
    }

    /** 테두리 2px짜리 칸 — 안은 빗금 그림 */
    private fun IntArray.panel(x0: Int, x1: Int, y0: Int, y1: Int, frame: Int = black) {
        art(x0, x1, y0, y1)
        for (y in y0 until y1) for (x in x0 until x1) {
            if (y < y0 + 2 || y >= y1 - 2 || x < x0 + 2 || x >= x1 - 2) this[y * w + x] = frame
        }
    }

    private fun borders(px: IntArray, topBottom: Boolean = true, leftRight: Boolean = true) =
        findBorders(px, w, h, topBottom, leftRight)

    @Test
    fun `흰 여백을 칸 테두리까지 잰다 - 여백의 먼지 몇 점은 봐준다`() {
        val px = IntArray(w * h) { white }
        px.panel(20, 180, 30, 270)
        // 위 여백의 먼지 한 점
        px[10 * w + 100] = black

        assertArrayEquals(intArrayOf(30, 30, 20, 20), borders(px))
    }

    @Test
    fun `위아래만, 좌우만 고르면 그 변만 잰다`() {
        val px = IntArray(w * h) { white }
        px.panel(20, 180, 30, 270)

        assertArrayEquals(intArrayOf(30, 30, 0, 0), borders(px, leftRight = false))
        assertArrayEquals(intArrayOf(0, 0, 20, 20), borders(px, topBottom = false))
    }

    @Test
    fun `위아래는 테두리가 있는 곳에 맞춘다 - 칸 밖으로 나온 그림과 쪽 번호도 함께 자른다`() {
        val px = IntArray(w * h) { white }
        // 1단: 왼쪽 칸은 쪽 위 끝까지 그렸고(탑), 오른쪽 칸은 테두리 위가 여백 — 열혈강호 1권 31쪽
        px.art(0, 90, 0, 140)
        px.panel(100, 190, 30, 140)
        // 2단: 아래 여백에 쪽 번호, 칸 밖 아래로 옷자락이 나왔다
        px.panel(10, 190, 150, 270)
        px.art(120, 160, 270, h)
        for (y in 280 until 286) for (x in 95 until 105) px[y * w + x] = black

        // 위·아래는 테두리 선에서 자른다. 왼쪽은 칸이 쪽 끝까지 그려져 안 자르고, 오른쪽은 모든 줄이 여백인 10만
        assertArrayEquals(intArrayOf(30, 30, 0, 10), borders(px))
    }

    @Test
    fun `위아래에 칸 테두리 선이 없으면(열린 칸, 칸 밖 글) 모든 열이 여백인 곳까지만 자른다`() {
        val px = IntArray(w * h) { white }
        // 테두리 없이 위아래로 열린 칸 — 가는 세로 획(머리카락·빗줄기)만 y=20..259, 가로로 긴 선은 없다 (나루토 1권 58쪽)
        for (x in 20..180 step 20) for (y in 20 until 260) {
            px[y * w + x] = black
            px[y * w + x + 1] = black
        }

        // 30% 열은 더 깊이 비었지만 테두리 선이 아니라서 따라가지 않는다
        assertArrayEquals(intArrayOf(20, 40, 20, 18), borders(px))
    }

    @Test
    fun `좌우는 칸 밖으로 나온 그림이 있으면 그만큼 덜 자른다`() {
        val px = IntArray(w * h) { white }
        px.panel(20, 180, 30, 270)
        // 오른쪽 여백으로 나온 칼끝 (줄 20개)
        for (y in 100 until 120) for (x in 180 until 190) px[y * w + x] = black

        assertArrayEquals(intArrayOf(30, 30, 20, 10), borders(px))
    }

    @Test
    fun `검은 바탕에 스캔한 쪽은 검은 테두리를 잰다`() {
        val px = IntArray(w * h) { black }
        px.panel(15, 190, 25, 280, frame = white)

        assertArrayEquals(intArrayOf(25, 20, 15, 10), borders(px))
    }

    @Test
    fun `쪽 끝의 얇은 검은 스캔 선은 건너 그 안쪽 흰 여백을 잰다`() {
        val px = IntArray(w * h) { white }
        // 왼쪽 끝 2열 검은 스캔 선
        for (y in 0 until h) for (x in 0 until 2) px[y * w + x] = black
        px.panel(20, 180, 30, 270)

        assertArrayEquals(intArrayOf(30, 30, 20, 20), borders(px))
    }

    @Test
    fun `흰 종이 쪽 아래 끝까지 이어진 검은 그림은 테두리가 아니다`() {
        val px = IntArray(w * h) { white }
        px.panel(20, 180, 30, 200)
        // 아래 100줄은 쪽 끝까지 검은 밤하늘
        for (y in 200 until h) for (x in 0 until w) px[y * w + x] = black

        assertArrayEquals(intArrayOf(30, 0, 0, 0), borders(px))
    }

    @Test
    fun `중간 밝기 여백(회색 망점)은 테두리로 치지 않는다`() {
        val px = IntArray(w * h) { gray }
        px.panel(20, 180, 30, 270)

        assertNull(borders(px))
    }

    @Test
    fun `쪽 끝까지 그린 쪽은 테두리가 없다`() {
        val px = IntArray(w * h)
        px.art(0, w, 0, h)

        assertNull(borders(px))
    }

    @Test
    fun `한 변에서 쪽의 20퍼센트까지만 잰다 - 빈 쪽도 다 지우지 않는다`() {
        val px = IntArray(w * h) { white }

        assertArrayEquals(intArrayOf(60, 60, 40, 40), borders(px))
    }

    @Test
    fun `두 쪽 보기에서는 다른 쪽 테두리에 맞춘다 - 바깥은 바깥끼리, 안쪽은 안쪽끼리`() {
        // 이 쪽은 위 2%만, 다른 쪽은 위 8%, 아래 4%, 바깥 6%, 안쪽 2%
        val crop = matchCrop(floatArrayOf(0.02f, 0f, 0f, 0f), floatArrayOf(0.08f, 0.04f, 0.06f, 0.02f))

        assertArrayEquals(floatArrayOf(0.08f, 0.04f, 0.06f, 0.02f), crop, 0f)
        // 한 쪽 보기면 이 쪽 그대로
        assertArrayEquals(floatArrayOf(0.02f, 0f, 0f, 0f), matchCrop(floatArrayOf(0.02f, 0f, 0f, 0f), null), 0f)
    }

    @Test
    fun `검은 바탕에 스캔한 쪽의 외곽 줄에 센서 빛샘이나 노이즈가 있어도 검은 테두리를 잰다`() {
        val px = IntArray(w * h) { black }
        px.panel(15, 190, 25, 280, frame = white)
        // 오른쪽 맨 바깥 줄(x=w-1)에 스캐너 반사광/센서 빛샘 (밝기 110)
        val bleed = 0xFF6E6E6E.toInt() // lum ~110
        for (y in 0 until h) px[y * w + w - 1] = bleed

        // 오른쪽은 빛샘 1줄을 건너뛰고도 검은 테두리 10을 잰다
        assertArrayEquals(intArrayOf(25, 20, 15, 10), borders(px))
    }

    @Test
    fun `좌우 변에서 컷 밖으로 튀어나온 그림은 몇 줄 되지 않아도 자르지 않는다`() {
        val px = IntArray(w * h) { white }
        px.panel(20, 180, 30, 270)
        // 오른쪽 여백으로 나온 칼끝이 단 2줄 (전체 높이의 1% 미만이어도 자르지 않아야 함)
        for (y in 100 until 102) for (x in 180 until 190) px[y * w + x] = black

        assertArrayEquals(intArrayOf(30, 30, 20, 10), borders(px))
    }

    @Test
    fun `좌우 변에서 1줄짜리 고립된 먼지 점은 봐주고 테두리까지 잰다`() {
        val px = IntArray(w * h) { white }
        px.panel(20, 180, 30, 270)
        // 오른쪽 여백 한가운데에 1줄짜리 먼지 점 1개 (x=190, y=100)
        px[100 * w + 190] = black

        assertArrayEquals(intArrayOf(30, 30, 20, 20), borders(px))
    }
}
