package com.komgatv.feature.reader.enhance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorizerTest {

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    /** 모델 대신 — 온 쪽을 [r], [g], [b] 한 색으로 칠한다 */
    private fun solid(r: Float, g: Float, b: Float, calls: MutableList<Int> = mutableListOf()): (FloatArray, Int) -> FloatArray =
        { gray, h ->
            calls += h
            assertEquals(COLOR_WIDTH * h, gray.size)
            assertEquals(0, h % 32)
            val plane = COLOR_WIDTH * h
            FloatArray(3 * plane) { i -> if (i < plane) r else if (i < 2 * plane) g else b }
        }

    @Test
    fun `회색 곳은 모델 색을 입고, 밝기는 그대로다 - 선과 종이는 칠하지 않는다`() {
        val w = 100
        val h = 150
        // 위 1/3 종이(250), 가운데 1/3 회색(128), 아래 1/3 선(10)
        val px = IntArray(w * h) { i -> gray(if (i / w < 50) 252 else if (i / w < 100) 128 else 10) }

        val out = requireNotNull(colorizePage(px, w, h, solid(0.9f, 0.3f, 0.2f)))

        val mid = out[75 * w + 50]
        assertTrue("빨갛게 칠한다: ${r(mid)},${g(mid)},${b(mid)}", r(mid) > g(mid) + 40 && r(mid) > b(mid) + 40)
        assertEquals(128.0, (r(mid) * 299 + g(mid) * 587 + b(mid) * 114) / 1000.0, 2.0)
        val paper = out[25 * w + 50]
        assertEquals(gray(252), paper)
        val line = out[125 * w + 50]
        assertEquals(gray(10), line)
    }

    @Test
    fun `이미 색이 있는 쪽은 그대로 둔다`() {
        val w = 64
        val h = 96
        val px = IntArray(w * h) { (0xFF shl 24) or (200 shl 16) or (60 shl 8) or 40 }

        assertNull(colorizePage(px, w, h, solid(0f, 0f, 1f)))
    }

    @Test
    fun `좌우 두 쪽 스캔은 쪽마다 따로 칠한다`() {
        val w = 300
        val h = 200
        val px = IntArray(w * h) { gray(128) }
        val calls = mutableListOf<Int>()

        requireNotNull(colorizePage(px, w, h, solid(0.2f, 0.8f, 0.3f, calls)))

        // 쪽마다 150×200 → 폭 384면 높이 512 (32의 배수)
        assertEquals(listOf(512, 512), calls)
    }
}
