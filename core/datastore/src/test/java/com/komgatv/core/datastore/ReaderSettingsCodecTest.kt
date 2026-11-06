package com.komgatv.core.datastore

import com.komgatv.core.model.ImageEnhancement
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.PageTransition
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.model.ReaderType
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSettingsCodecTest {

    @Test
    fun `모든 값을 그대로 되돌린다`() {
        val s = ReaderSettings(
            readerType = ReaderType.CONTINUOUS,
            pageLayout = PageLayout.DOUBLE_PAGES,
            scaleType = ScaleType.FIT_WIDTH,
            readingDirection = ReadingDirection.RIGHT_TO_LEFT,
            continuousPadding = 0.15f,
            continuousPageSpacing = 24,
            imageEnhancement = ImageEnhancement.SHARPEN,
            zoomPercent = 117,
            contrastPercent = 125,
            brightnessPercent = 85,
            pageTransition = PageTransition.FADE,
        )
        assertEquals(s, ReaderSettingsCodec.decode(ReaderSettingsCodec.encode(s)))
    }

    @Test
    fun `확대는 100~120 밖이면 끝값, 없으면 100`() {
        assertEquals(120, ReaderSettingsCodec.decode("zoomPercent=999")!!.zoomPercent)
        assertEquals(100, ReaderSettingsCodec.decode("zoomPercent=20")!!.zoomPercent)
        assertEquals(100, ReaderSettingsCodec.decode("pageLayout=DOUBLE_PAGES")!!.zoomPercent)
    }

    @Test
    fun `화면 전환이 없던 예전 설정은 밀기`() {
        assertEquals(PageTransition.SLIDE, ReaderSettingsCodec.decode("pageLayout=DOUBLE_PAGES")!!.pageTransition)
    }

    @Test
    fun `없으면 null, 모르는 키와 잘못된 값은 기본값`() {
        assertNull(ReaderSettingsCodec.decode(null))
        assertNull(ReaderSettingsCodec.decode(""))
        // stretchToFit은 없어진 키 — 예전 저장값에 있어도 무시된다
        val s = ReaderSettingsCodec.decode("pageLayout=DOUBLE_PAGES;scaleType=NOPE;future=1;stretchToFit=false")!!
        assertEquals(PageLayout.DOUBLE_PAGES, s.pageLayout)
        assertEquals(ScaleType.SCREEN, s.scaleType)
    }
}
