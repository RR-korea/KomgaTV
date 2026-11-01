package com.komgatv.feature.reader.translate

import com.komgatv.core.model.TranslationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBlocksTest {

    private fun line(l: Float, t: Float, r: Float, b: Float, text: String) = OcrLine(Box(l, t, r, b), text)

    @Test
    fun `세로쓰기는 오른쪽 열부터 붙여 읽는다`() {
        // 가는 세로 열 두 개: 오른쪽이 먼저
        val block = OcrBlock(
            Box(0.1f, 0.1f, 0.2f, 0.4f),
            listOf(line(0.10f, 0.1f, 0.13f, 0.4f, "です"), line(0.16f, 0.1f, 0.19f, 0.4f, "そう")),
        )
        assertTrue(block.isVertical())
        assertEquals("そうです", block.sourceText(TranslationMode.JA_KO))
    }

    @Test
    fun `가로쓰기 영어는 위 줄부터 띄워 잇고, 나뉜 단어는 붙인다`() {
        val block = OcrBlock(
            Box(0.1f, 0.1f, 0.5f, 0.2f),
            listOf(line(0.1f, 0.15f, 0.5f, 0.2f, "ROW."), line(0.1f, 0.1f, 0.5f, 0.14f, "WE LEAVE TOMOR-")),
        )
        assertFalse(block.isVertical())
        assertEquals("We leave tomorrow.", block.sourceText(TranslationMode.EN_KO))
    }

    @Test
    fun `대문자 영어는 문장 첫 글자와 I만 대문자로`() {
        assertEquals("I can't. You go! Where am I?", normalizeEnglish("I CAN'T. YOU GO! WHERE AM I?"))
        // 대소문자가 섞인 글은 그대로
        assertEquals("Hello Komga", normalizeEnglish("Hello Komga"))
    }

    @Test
    fun `글자가 없거나 숫자뿐이거나 이미 한국어거나 고른 언어가 아니면 번역하지 않는다`() {
        assertFalse(worthTranslating("!!..", TranslationMode.JA_KO))
        assertFalse(worthTranslating("A", TranslationMode.EN_KO))
        assertFalse(worthTranslating("9788970098 ISBN 89-7019-849-7", TranslationMode.EN_KO))
        assertFalse(worthTranslating("윙크 COLLECTION", TranslationMode.EN_KO))
        // 일본어 인식기가 한국어를 잘못 읽은 잡음: 로마자 사이에 한자
        assertFalse(worthTranslating("0- 大人く qmi sun 9g", TranslationMode.EN_KO))
        assertFalse(worthTranslating("HELLO", TranslationMode.JA_KO))
        assertTrue(worthTranslating("あ", TranslationMode.JA_KO))
        assertTrue(worthTranslating("NO", TranslationMode.EN_KO))
        assertTrue(worthTranslating("WINK COLLECTION", TranslationMode.EN_KO))
    }

    @Test
    fun `겹치거나 맞닿은 같은 방향 덩어리는 합치고 떨어진 것은 둔다`() {
        val a = OcrBlock(Box(0.1f, 0.1f, 0.3f, 0.15f), listOf(line(0.1f, 0.1f, 0.3f, 0.15f, "HEY")))
        val b = OcrBlock(Box(0.1f, 0.152f, 0.3f, 0.2f), listOf(line(0.1f, 0.152f, 0.3f, 0.2f, "YOU")))
        val far = OcrBlock(Box(0.6f, 0.6f, 0.8f, 0.65f), listOf(line(0.6f, 0.6f, 0.8f, 0.65f, "THERE")))

        val merged = mergeBlocks(listOf(a, b, far))

        assertEquals(2, merged.size)
        assertEquals(Box(0.1f, 0.1f, 0.3f, 0.2f), merged[0].box)
        assertEquals("Hey you", merged[0].sourceText(TranslationMode.EN_KO))
    }

    @Test
    fun `번역 저장은 되돌리면 같고, 판이 다르면 버린다`() {
        val blocks = listOf(
            TranslatedBlock(Box(0.1f, 0.2f, 0.3f, 0.4f), "안녕\t\"친구\"\n둘째 줄 \\ 끝"),
            TranslatedBlock(Box(0f, 0f, 1f, 1f), "가"),
        )
        assertEquals(blocks, TranslationCacheCodec.decode(TranslationCacheCodec.encode(blocks)))
        assertEquals(null, TranslationCacheCodec.decode("v0\n0,0,1,1\t옛것"))
    }
}
