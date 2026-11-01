package com.komgatv.feature.reader.enhance

import com.komgatv.core.model.ImageEnhancement
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsharpMaskTest {

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun channel(c: Int) = c and 0xFF

    @Test
    fun `평평한 그림은 바뀌지 않는다`() {
        val flat = IntArray(16) { gray(120) }
        assertArrayEquals(flat, unsharpMask(flat, 4, 4, 1.0f))
    }

    @Test
    fun `경계는 어두운 쪽은 더 어둡게 밝은 쪽은 더 밝게`() {
        // 왼쪽 두 칸 어둡고(50) 오른쪽 두 칸 밝다(200)
        val w = 4
        val h = 3
        val px = IntArray(w * h) { i -> if (i % w < 2) gray(50) else gray(200) }
        val out = unsharpMask(px, w, h, 1.0f)

        assertTrue(channel(out[1]) < 50)
        assertTrue(channel(out[2]) > 200)
        // 경계에서 먼 칸은 그대로
        assertEquals(50, channel(out[0]))
        assertEquals(200, channel(out[3]))
    }

    @Test
    fun `알파는 그대로, 값은 0~255로 자른다`() {
        val px = intArrayOf(0x80000000.toInt(), gray(255), gray(255), 0x80000000.toInt())
        val out = unsharpMask(px, 4, 1, 5f)
        assertEquals(0x80, out[0] ushr 24)
        assertEquals(255, channel(out[1]))
        assertEquals(0, channel(out[0]))
    }

    @Test
    fun `선택에 따른 필터와 세기`() {
        assertNull(ImageEnhancer.sharpenAmount(ImageEnhancement.OFF))
        assertNull(ImageEnhancer.sharpenAmount(ImageEnhancement.SMOOTH))
        assertTrue(ImageEnhancer.sharpenAmount(ImageEnhancement.SHARPEN_STRONG)!! > ImageEnhancer.sharpenAmount(ImageEnhancement.SHARPEN)!!)
    }
}
