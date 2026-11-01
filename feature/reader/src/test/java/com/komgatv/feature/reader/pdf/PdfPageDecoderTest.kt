package com.komgatv.feature.reader.pdf

import coil3.size.Dimension
import coil3.size.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageDecoderTest {

    @Test
    fun `요청 크기 안에 비율을 지켜 꼭 맞춘다`() {
        // 세로로 긴 쪽(500x750pt)을 1920x1080 화면에 — 높이가 먼저 닿는다
        assertEquals(720 to 1080, PdfPageDecoder.targetSize(500, 750, Size(1920, 1080)))
        // 너비 맞춤(높이는 넉넉히) — 너비가 먼저 닿는다
        assertEquals(960 to 1440, PdfPageDecoder.targetSize(500, 750, Size(960, 1080 * 4)))
    }

    @Test
    fun `한쪽만 정해지면 그쪽에, 아무것도 없으면 긴 변 2048`() {
        assertEquals(1000 to 1500, PdfPageDecoder.targetSize(500, 750, Size(Dimension.Pixels(1000), Dimension.Undefined)))
        assertEquals(1365 to 2048, PdfPageDecoder.targetSize(500, 750, Size.ORIGINAL))
    }

    @Test
    fun `긴 변은 4096을 넘지 않는다`() {
        assertEquals(4096 to 2048, PdfPageDecoder.targetSize(100, 50, Size(10000, 10000)))
    }
}
