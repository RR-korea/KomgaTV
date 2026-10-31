package com.komgatv.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedServerCodecTest {

    private val home = StoredServer("https://nas.example.com/komga", "basic", "me@example.com", "enc:pw")
    private val work = StoredServer("http://10.0.0.5:25600", "apikey", "", "enc:key:with:colons\nand newline")

    @Test
    fun `어떤 글자가 들어 있어도 그대로 되돌린다`() {
        val text = SavedServerCodec.encode(listOf(home, work))
        assertEquals(listOf(home, work), SavedServerCodec.decode(text))
    }

    @Test
    fun `빈 값과 깨진 줄은 건너뛴다`() {
        assertEquals(emptyList<StoredServer>(), SavedServerCodec.decode(null))
        val text = SavedServerCodec.encode(listOf(home)) + "\n깨진줄\n"
        assertEquals(listOf(home), SavedServerCodec.decode(text))
    }

    @Test
    fun `같은 서버와 계정이면 바꾸고, 다르면 붙인다`() {
        val updated = home.copy(secret = "enc:new")
        assertEquals(listOf(updated, work), SavedServerCodec.upsert(listOf(home, work), updated))
        val otherUser = home.copy(username = "kid@example.com")
        assertEquals(3, SavedServerCodec.upsert(listOf(home, work), otherUser).size)
    }

    @Test
    fun `API Key는 서버당 하나로 본다`() {
        assertEquals("http://10.0.0.5:25600|apikey", work.id)
        assertEquals("https://nas.example.com/komga|me@example.com", home.id)
    }
}
