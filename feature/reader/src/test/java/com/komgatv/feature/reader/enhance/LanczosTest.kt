package com.komgatv.feature.reader.enhance

import coil3.size.Size
import com.komgatv.core.model.ScaleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanczosTest {

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun channel(c: Int) = c and 0xFF

    @Test
    fun `평평한 그림은 키우거나 줄여도 같은 색`() {
        val flat = IntArray(8 * 6) { gray(120) }
        assertTrue(lanczosResize(flat, 8, 6, 20, 15).all { it == gray(120) })
        assertTrue(lanczosResize(flat, 8, 6, 3, 2).all { it == gray(120) })
    }

    @Test
    fun `키우면 크기가 맞고 경계 양쪽 끝 색은 남는다`() {
        // 왼쪽 반 검정, 오른쪽 반 흰색
        val w = 8
        val h = 4
        val px = IntArray(w * h) { i -> if (i % w < w / 2) gray(0) else gray(255) }
        val out = lanczosResize(px, w, h, 24, 12)
        assertEquals(24 * 12, out.size)
        assertEquals(0, channel(out[0]))
        assertEquals(255, channel(out[23]))
        // 알파는 그대로 불투명
        assertTrue(out.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun `창 크기 2·3·4 모두 크기와 평평한 색을 지킨다`() {
        val flat = IntArray(10 * 10) { gray(90) }
        for (a in 2..4) {
            val out = lanczosResize(flat, 10, 10, 27, 31, a)
            assertEquals(27 * 31, out.size)
            assertTrue(out.all { it == gray(90) })
        }
    }

    @Test
    fun `크기 맞춤별로 그릴 크기`() {
        val box = Size(720, 1080)
        // 600x900 쪽을 화면 맞춤 → 칸 안에 꼭 (높이가 먼저 닿는다)
        assertEquals(720 to 1080, fittedSize(600, 900, box, ScaleType.SCREEN))
        assertEquals(720 to 1080, fittedSize(600, 900, Size(720, 4320), ScaleType.FIT_WIDTH))
        assertEquals(720 to 1080, fittedSize(600, 900, Size(1440, 1080), ScaleType.FIT_HEIGHT))
        assertNull(fittedSize(600, 900, box, ScaleType.ORIGINAL))
    }
}
