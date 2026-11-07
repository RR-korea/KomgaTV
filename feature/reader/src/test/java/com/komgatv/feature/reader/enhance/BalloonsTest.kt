package com.komgatv.feature.reader.enhance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalloonsTest {

    private val white = -1
    private val black = 0xFF000000.toInt()
    private val w = 400
    private val h = 600

    /** 중심 ([cx], [cy]), 반지름 [rx]×[ry]인 타원 테두리 (두께 2칸) */
    private fun IntArray.ellipse(cx: Int, cy: Int, rx: Int, ry: Int) {
        for (y in 0 until h) for (x in 0 until w) {
            val dx = (x - cx).toDouble() / rx
            val dy = (y - cy).toDouble() / ry
            val r = dx * dx + dy * dy
            if (r in 0.95..1.05) this[y * w + x] = black
        }
    }

    /** [top]부터 10줄 높이 글자 — [x0]부터 12칸마다 6칸 너비, 가운데 구멍 */
    private fun IntArray.text(x0: Int, x1: Int, top: Int) {
        for (gx in x0 until x1 step 12) for (y in top until top + 10) for (x in gx until gx + 6) {
            val hole = y in top + 3 until top + 7 && x in gx + 2 until gx + 4
            if (!hole) this[y * w + x] = black
        }
    }

    @Test
    fun `잘리는 곳에 걸린 대사를 말풍선 안에서 쪽 가운데 쪽으로 옮긴다`() {
        val px = IntArray(w * h) { white }
        px.ellipse(200, 45, 80, 40)
        px.text(170, 230, 20)

        val out = requireNotNull(pullBalloonText(px, w, h, topCut = 40, bottomCut = 40, moveBalloons = false))

        // 위로 40줄이 잘린다 — 대사(20~29줄)를 잘리는 선 + 여백(6줄) 아래(46~55줄)로
        assertEquals((46..55).toList(), (12 until 78).filter { out[it * w + 170] == black })
        // 원래 자리는 비고, 말풍선 테두리는 그대로
        assertTrue((20..29).none { out[it * w + 172] == black })
        assertEquals((0 until h).filter { px[it * w + 200] == black && it !in 10..80 }, (0 until h).filter { out[it * w + 200] == black && it !in 10..80 })
    }

    @Test
    fun `쪽 끝에 걸린 말풍선은 옵션을 켜면 말풍선째 밀어 대사가 잘리지 않게 한다`() {
        val px = IntArray(w * h) { white }
        // 쪽 위 끝에 걸려 잘린 말풍선 (아래 절반 타원) — 대사가 넓어 말풍선 안에서는 다 못 옮긴다
        px.ellipse(200, 0, 80, 70)
        px.text(140, 260, 5)

        val inside = requireNotNull(pullBalloonText(px, w, h, topCut = 40, bottomCut = 40, moveBalloons = false))
        val insideTop = (0 until 120).first { inside[it * w + 140] == black }
        assertTrue("말풍선 안에서는 조금만: $insideTop", insideTop in 6 until 40)

        val pushed = requireNotNull(pullBalloonText(px, w, h, topCut = 40, bottomCut = 40, moveBalloons = true))
        val pushedTop = (0 until 120).first { pushed[it * w + 140] == black }
        assertTrue("말풍선째 밀면 잘리는 선 아래로: $pushedTop", pushedTop >= 40)
        // 말풍선 아래 끝도 함께 내려온다
        assertTrue(pushed[(70 + 40) * w + 200] == black || pushed[(71 + 40) * w + 200] == black)
    }

    @Test
    fun `큰 칸 안의 글은 말풍선이 아니라 그대로 둔다`() {
        val px = IntArray(w * h) { white }
        // 쪽의 6%보다 넓은 칸
        for (y in 10 until 300) for (x in 20 until 380) {
            if (y < 12 || y >= 298 || x < 22 || x >= 378) px[y * w + x] = black
        }
        px.text(170, 230, 20)

        assertNull(pullBalloonText(px, w, h, topCut = 40, bottomCut = 40, moveBalloons = true))
    }

    @Test
    fun `잘리는 곳 밖의 대사와 검은 바탕 쪽은 그대로`() {
        val px = IntArray(w * h) { white }
        px.ellipse(200, 300, 80, 40)
        px.text(170, 230, 295)
        assertNull(pullBalloonText(px, w, h, topCut = 40, bottomCut = 40, moveBalloons = true))

        val dark = IntArray(w * h) { black }
        assertNull(pullBalloonText(dark, w, h, topCut = 40, bottomCut = 40, moveBalloons = true))
    }

    @Test
    fun `확대로 잘리는 줄 수 — 가운데 기준, 위아래 검은 띠는 빼고`() {
        // 화면 1080줄을 꽉 채운 쪽, 120% — 위아래 각각 (1 − 1/1.2) / 2 = 1/12
        assertEquals(90, zoomCutRows(760, 1080, 120, 1920, 1080, fitHeight = false))
        // 두 배로 줄여 그리는 쪽도 쪽 줄 수로
        assertEquals(180, zoomCutRows(760, 2160, 120, 1920, 1080, fitHeight = false))
        // 위아래에 40줄씩 검은 띠가 있으면 그만큼 덜 잘린다
        assertEquals(50, zoomCutRows(1920, 1000, 120, 1920, 1080, fitHeight = false))
        assertEquals(0, zoomCutRows(760, 1080, 100, 1920, 1080, fitHeight = false))
    }
}
