package com.komgatv.feature.reader.enhance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

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
        for (y in 165 until 175) for (x in 30 until 60) if (x % 6 < 3) px[y * w + x] = black

        val out = requireNotNull(collapseGutters(px, w, h))

        // 칸 100줄 + 칸 100줄 = 200줄이 붙고, 위아래 100줄씩 비운다
        val blackRows = (0 until h).filter { out[it * w + 45] == black }
        assertEquals(listOf(100, 101, 198, 199, 200, 297, 298), blackRows)
        // 좌우는 그대로
        assertEquals(black, out[150 * w + 10])
        assertEquals(white, out[150 * w + 9])
    }

    @Test
    fun `칸 겹치기 - 칸 줄끼리 쪽 높이의 그만큼 포개고 가운데 줄을 위에 그린다`() {
        val px = IntArray(w * h) { white }
        px.panel(20)
        px.panel(150)
        px.panel(280)
        // 가운데 칸 안은 빗금 — 어느 칸이 위에 그려졌는지 본다
        for (y in 152 until 248) for (x in 12 until 88) if ((x + y) % 4 == 0) px[y * w + x] = black

        val out = requireNotNull(collapseGutters(px, w, h, overlapPercent = 5))

        // 100줄 칸 셋이 20줄(5%)씩 포개져 260줄 — 가운데(70~329). 1단 70~169, 2단 150~249, 3단 230~329
        assertEquals((70..329).toList(), (0 until h).filter { out[it * w + 10] == black })
        // 2단이 1단 위 — 1단 아래 테두리(168·169) 자리에 2단의 빗금이 보인다
        assertEquals(black, out[168 * w + 12])
        assertEquals(white, out[168 * w + 13])
        // 2단이 3단 위 — 3단 위 테두리(230·231) 자리에도 2단의 빗금
        assertEquals(white, out[230 * w + 13])
        assertEquals(black, out[230 * w + 14])
        // 2단의 위·아래 테두리는 그대로
        assertEquals(black, out[150 * w + 45])
        assertEquals(black, out[249 * w + 45])
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
        assertEquals(listOf(161, 162, 219, 220, 221, 278, 279), (0 until h).filter { out[it * w + 20] == black })
    }

    @Test
    fun `칸 묶음 사이 여백의 쪽 번호는 여백과 함께 지운다`() {
        val px = IntArray(w * h) { white }
        // 오른쪽 세로로 긴 칸, 왼쪽에 작은 칸 둘 (사이 40줄) — 그 사이 여백 왼쪽에 쪽 번호 ("10", 획 높이 10줄)
        px.box(55, 95, 20, 180)
        px.box(6, 50, 20, 80)
        px.box(6, 50, 120, 180)
        for (y in 95 until 105) {
            px[y * w + 10] = black
            for (x in 13..16) if (y == 95 || y == 104 || x == 13 || x == 16) px[y * w + x] = black
        }

        val out = requireNotNull(collapseGutters(px, w, h))

        // 쪽 번호가 없을 때처럼 작은 칸 둘이 맞붙는다
        assertEquals(listOf(161, 162, 219, 220, 221, 278, 279), (0 until h).filter { out[it * w + 20] == black })
        // 쪽 번호는 남지 않는다 (작은 칸 테두리 2줄씩만)
        assertEquals(listOf(161, 162, 219, 220, 221, 278, 279), (0 until h).filter { out[it * w + 13] == black })
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
        assertEquals(listOf(100, 101, 198, 199, 200, 297, 298), (0 until h).filter { out[it * w + 45] == black })
        // 상자 왼쪽 테두리는 끊기지 않고 160~248로 이어진다 (칸 A 위 테두리 100·101, 칸 B 아래 테두리 297·298)
        val boxRows = (0 until h).filter { out[it * w + 60] == black }
        assertEquals(listOf(100, 101) + (160..248).toList() + listOf(297, 298), boxRows)
    }

    @Test
    fun `기울어진 스캔의 테두리 끝자락이 여백에 걸쳐도 테두리를 자르지 않고 칸을 붙인다`() {
        val px = IntArray(w * h) { white }
        px.panel(40)
        px.panel(200)
        // 칸 A 아래 테두리가 오른쪽으로 갈수록 3줄 내려가고(138~142), 칸 B 위 테두리도 기울었다(196~200)
        for (x in 10 until 90) {
            val down = (x - 10) * 4 / 80
            for (y in 138 + down until 140 + down) px[y * w + x] = black
            for (y in 196 + down until 198 + down) px[y * w + x] = black
        }

        val out = requireNotNull(collapseGutters(px, w, h))

        // 오른쪽 끝(x=80) 테두리 줄은 맞닿는 테두리가 1줄 겹치는 것 말고는 잘리지 않는다
        assertEquals((0 until h).count { px[it * w + 80] == black } - 1, (0 until h).count { out[it * w + 80] == black })
        // 칸 사이 흰 곳은 없어진다 — 칸 A 아래 테두리 끝(142)과 칸 B 위 테두리 시작(196) 사이 53줄에 더해
        // 기울어진 테두리 끝자락과 맞닿는 테두리가 4줄 겹친다
        val before = (0 until h).filter { px[it * w + 80] == black }
        val after = (0 until h).filter { out[it * w + 80] == black }
        assertEquals(57, (before.last() - before.first()) - (after.last() - after.first()))
    }

    @Test
    fun `쪽 위아래 끝까지 찬 펼침 쪽도 칸 묶음 안의 여백은 붙인다`() {
        val px = IntArray(w * h) { white }
        // 왼쪽은 쪽 높이 전부를 차는 칸, 오른쪽은 위아래 끝에 닿은 작은 칸 둘 (사이 40줄) — 쪽 전체를 가로지르는 여백이 없다
        for ((x0, x1, y0, y1) in listOf(listOf(6, 45, 0, h), listOf(55, 95, 0, 180), listOf(55, 95, 220, h))) {
            px.box(x0, x1, y0, y1)
            // 칸 안 그림 (빗금)
            for (y in y0 + 5 until y1 - 5) for (x in x0 + 5 until x1 - 5) if ((x + y) % 7 == 0) px[y * w + x] = black
        }

        val out = requireNotNull(collapseGutters(px, w, h))

        // 왼쪽 칸은 그대로, 오른쪽 칸 둘은 맞붙어 가운데로
        assertEquals(listOf(0, 1, 398, 399), (0 until h).filter { out[it * w + 9] == black })
        assertEquals(listOf(20, 21, 198, 199, 200, 377, 378), (0 until h).filter { out[it * w + 58] == black })
    }

    @Test
    fun `쪽 끝에 바짝 붙은 칸 테두리는 칸이다`() {
        val wide = 400
        val px = IntArray(wide * h) { white }
        // 좌우 테두리가 쪽 끝(2% 안)에 붙은 칸 둘, 사이 60줄
        for ((y0, y1) in listOf(40 to 140, 200 to 300)) for (y in y0 until y1) for (x in 2 until 398) {
            if (y < y0 + 2 || y >= y1 - 2 || x < 4 || x >= 396) px[y * wide + x] = black
        }

        val out = requireNotNull(collapseGutters(px, wide, h))

        // 칸 안쪽 빈 곳은 그대로 두고 사이 여백만 없앤다
        assertEquals(listOf(100, 101, 198, 199, 200, 297, 298), (0 until h).filter { out[it * wide + 200] == black })
        assertEquals(199, (0 until h).count { out[it * wide + 2] == black })
    }

    @Test
    fun `칸 사이 여백에 좁게 걸친 효과음은 여백과 함께 짧아진다`() {
        val px = IntArray(w * h) { white }
        px.panel(40)
        px.box(10, 90, 160, 260)
        // 칸 A 아래쪽부터 칸 B 위쪽까지 걸친 효과음 — 폭 7칸, 획 높이 3줄
        for (y in 120 until 180) for (x in 20..26) if (y % 6 < 3) px[y * w + x] = black

        val out = requireNotNull(collapseGutters(px, w, h))

        // 칸 100줄 + 칸 100줄이 맞붙어 가운데로 — 맞닿는 테두리는 1줄 겹친다
        assertEquals(listOf(100, 101, 198, 199, 200, 297, 298), (0 until h).filter { out[it * w + 45] == black })
    }

    /** 칸 A(위 테두리 40줄, 아래 테두리는 [slant]만큼 기울었다)와 그 [gap]줄 아래에서 같은 기울기로 시작해 300줄에서 끝나는 칸 B */
    private fun IntArray.slantedPanels(gap: Int, slant: (Int) -> Int) {
        for (x in 10 until 90) {
            val b = slant(x)
            for (y in 40 until b + 2) if (y < 42 || y >= b || x < 12 || x >= 88) this[y * w + x] = black
            for (y in b + 2 + gap until 302) if (y < b + 4 + gap || y >= 300 || x < 12 || x >= 88) this[y * w + x] = black
        }
    }

    @Test
    fun `비스듬한 칸 테두리 사이 여백도 기울기 그대로 붙이고, 아래 칸은 일그러지지 않는다`() {
        val px = IntArray(w * h) { white }
        // 아래 테두리가 왼쪽 140줄에서 오른쪽 152줄로 기운다 (여백 10줄보다 더) — 쪽 전체를 가로지르는 빈 줄이 없다
        val slant = { x: Int -> 140 + (x - 10) * 12 / 79 }
        px.slantedPanels(gap = 10, slant = slant)

        val out = requireNotNull(collapseGutters(px, w, h))

        fun blackRows(x: Int) = (0 until h).filter { out[it * w + x] == black }
        for (x in listOf(12, 30, 50, 70, 87)) {
            val rows = blackRows(x)
            // 위 테두리 2줄, A 아래 테두리 2줄, B 위 테두리 2줄, B 아래 테두리 2줄 — A·B 사이 빈 줄은 10줄에서 4줄 이하로
            assertEquals(8, rows.size)
            assertEquals(true, rows[4] - rows[3] - 1 <= 4)
            // A 안쪽 높이는 그대로
            assertEquals(slant(x) - 40, rows[2] - rows[0])
        }
        // 칸 B는 통째로 올라왔다 — 아래 테두리가 모든 열에서 같은 줄, 기울기도 그대로
        assertEquals(1, listOf(12, 30, 50, 70, 87).map { blackRows(it)[6] }.distinct().size)
        assertEquals(slant(87) - slant(12), blackRows(87)[4] - blackRows(12)[4])
    }

    @Test
    fun `그림 속 들쭉날쭉한 흰 틈은 비스듬한 여백으로 보지 않는다`() {
        val px = IntArray(w * h) { white }
        // 위아래 칸 사이 흰 틈이 열마다 크게 오르내린다 (그림 속 빈 곳)
        px.slantedPanels(gap = 18) { x -> 150 + (x * 37) % 23 }

        val out = collapseGutters(px, w, h)

        // 틈은 줄지 않는다 (통째로 옮겨졌을 수는 있다)
        assertRowsKept(px, out, w, 50)
    }

    @Test
    fun `좌우 두 쪽을 한 장에 스캔한 그림은 쪽마다 따로 칸을 붙인다 - 가운데 접힌 곳 그림자는 잉크가 아니다`() {
        val wide = 260
        val px = IntArray(wide * h) { white }
        // 가운데 접힌 곳 그림자 — 100~159열, 가운데로 갈수록 어둡다 (가장 어두운 곳 밝기 150). 칸 안쪽까지 걸친다
        fun shade(x: Int): Int {
            val v = 255 - maxOf(0, 30 - abs(x - 130)) * 105 / 30
            return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        for (y in 0 until h) for (x in 100 until 160) px[y * wide + x] = shade(x)
        fun box(x0: Int, x1: Int, y0: Int, y1: Int) {
            for (y in y0 until y1) for (x in x0 until x1) {
                if (y < y0 + 2 || y >= y1 - 2 || x < x0 + 2 || x >= x1 - 2) px[y * wide + x] = black
            }
        }
        // 왼쪽 쪽: 칸 사이 200~239줄, 오른쪽 쪽: 칸 사이 120~159줄 — 쪽 전체를 가로지르는 여백이 없다
        box(10, 122, 40, 200)
        box(10, 122, 240, 360)
        box(138, 250, 40, 120)
        box(138, 250, 160, 360)

        val out = requireNotNull(collapseGutters(px, wide, h))

        // 두 쪽 다 칸 둘이 맞붙어 맞닿는 테두리가 1줄 겹친다 (높이 279줄)
        for (x in listOf(60, 200)) {
            val rows = (0 until h).filter { out[it * wide + x] == black }
            assertEquals(7, rows.size)
            assertEquals(rows[2] + 1, rows[3])
            assertEquals(rows[3] + 1, rows[4])
            assertEquals(278, rows.last() - rows.first())
        }
        // 그림자는 그대로
        assertEquals(shade(130), out[200 * wide + 130])
    }

    @Test
    fun `세로 여백도 비스듬해 줄로 나눌 수 없는 칸은 칸마다 이웃 칸에 닿을 때까지 쪽 가운데 쪽으로 민다`() {
        val wide = 200
        val px = IntArray(wide * h) { white }
        // 왼쪽 칸 둘 (위 20~119, 아래 140~299, 사이 20줄)과 오른쪽 긴 칸 (20~299) — 그 사이 세로 여백이 비스듬하다
        fun leftEdge(y: Int) = 90 + (y - 20) / 10
        for (y in 20 until 300) for (x in 10 until 190) {
            val right = leftEdge(y)
            val inLeft = x < right && (y in 20 until 120 || y in 140 until 300)
            val inRight = x >= right + 12
            if (!inLeft && !inRight) continue
            val top = if (inRight) 20 else if (y < 120) 20 else 140
            val bottom = if (inRight) 299 else if (y < 120) 119 else 299
            val edge = y < top + 2 || y > bottom - 2 || x < 12 || x >= 188 || (inLeft && x >= right - 2) || (inRight && x < right + 14)
            // 칸 안 그림 (빗금)
            if (edge || (x + y) % 9 == 0) px[y * wide + x] = black
        }

        val out = requireNotNull(collapseGutters(px, wide, h))

        // 가로 테두리 줄 (왼쪽 칸 안쪽 20~60열이 모두 검정) — 위 칸 위·아래, 아래 칸 위·아래
        fun borders(img: IntArray) = (0 until h).filter { y -> (20 until 60).all { img[y * wide + it] == black } }
        val before = borders(px)
        val after = borders(out)
        assertEquals(listOf(20, 21, 118, 119, 140, 141, 298, 299), before)
        // 쪽 위쪽 띠라 위 왼쪽 칸이 아래 왼쪽 칸 바로 위로 내려왔다 — 두 칸 사이 20줄 여백이 없어지고, 칸 높이는 그대로
        assertEquals(8, after.size)
        assertEquals("$after", true, after[4] - after[3] in 1..2)
        assertEquals(before[7] - before[4], after[7] - after[4])
        assertEquals(before[3] - before[0], after[3] - after[0])
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
        assertEquals(listOf(100, 101, 198, 199, 200, 297, 298), blackRows)
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
        assertEquals(listOf(100, 101, 198, 199, 200, 297, 298), (0 until h).filter { out[it * w + 45] == black })
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
    fun `그림 속 검은 세로 줄기는 칸 묶음 사이 여백이 아니다 - 옆의 글과 검은 바탕 사이를 줄이지 않는다`() {
        val px = IntArray(w * h) { white }
        px.panel(20)
        // 왼쪽에 해설 글(140~175), 그 아래 흰 바탕을 건너 검은 바탕(220~319) — 띠 높이 내내 검은 줄기(38~49)로 오른쪽 칸과 이어진다
        for (y in 140 until 176) for (x in 6 until 14) if (((x * 7919 + y * 104729) xor (x * y)) % 9 < 6) px[y * w + x] = black
        for (y in 220 until 320) for (x in 2 until 38) px[y * w + x] = black
        for (y in 130 until 320) for (x in 38 until 50) px[y * w + x] = black
        px.box(50, 96, 130, 320)
        for (y in 134 until 316) for (x in 54 until 92) if ((x + y) % 9 == 0) px[y * w + x] = black

        val out = collapseGutters(px, w, h)

        assertRowsKept(px, out, w, 8)
    }

    /** [x] 열에서 검은 줄들이 (통째로 옮겨졌을 수는 있어도) 간격 그대로인지 */
    private fun assertRowsKept(input: IntArray, out: IntArray?, width: Int, x: Int) {
        val before = (0 until h).filter { input[it * width + x] == black }
        val after = (0 until h).filter { (out ?: input)[it * width + x] == black }
        assertEquals(before.size, after.size)
        assertEquals(before.map { it - before.first() }, after.map { it - after.first() })
    }

    @Test
    fun `글이 빽빽한 곳은 여백이 아니다 - 글 줄 사이를 지우지 않는다`() {
        val px = IntArray(w * h) { white }
        px.panel(20)
        px.panel(290)
        // 두 칸 사이 글 12줄 — 획 높이 8줄(짧아서 칸으로는 안 친다), 줄 사이 4줄
        for (t in 135 until 280 step 12) for (y in t until t + 8) for (x in 15 until 85) if (x % 6 < 3) px[y * w + x] = black

        val out = collapseGutters(px, w, h)

        assertRowsKept(px, out, w, 15)
    }

    @Test
    fun `액자 선만 지나가는 흰 줄은 여백이 아니다 - 목차 줄 사이를 지우지 않는다`() {
        val wide = 400
        val px = IntArray(wide * h) { white }
        // 쪽 높이 거의 전부를 두른 1px 액자, 안에 30줄마다 목차 한 줄
        for (y in 20 until 380) {
            px[y * wide + 40] = black
            px[y * wide + 360] = black
        }
        for (t in 40 until 360 step 30) for (y in t until t + 8) for (x in 60 until 340) if (x % 6 < 3) px[y * wide + x] = black

        val out = collapseGutters(px, wide, h)

        assertRowsKept(px, out, wide, 60)
    }

    @Test
    fun `지울 여백이 없으면 그대로`() {
        // 쪽 전체가 그림(세로 줄무늬)
        val px = IntArray(w * h) { i -> if (i % w % 10 < 3) black else white }
        assertNull(collapseGutters(px, w, h))
    }
}
