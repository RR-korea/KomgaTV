package com.komgatv.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenSeriesCodecTest {

    @Test
    fun `되돌리면 같고, 잘못된 조각은 버린다`() {
        val hidden = mapOf("0RHA" to 1000L, "0RHB" to 2000L)
        assertEquals(hidden, HiddenSeriesCodec.decode(HiddenSeriesCodec.encode(hidden)))
        assertEquals(mapOf("ok" to 5L), HiddenSeriesCodec.decode("ok=5;bad;=3;x=nope"))
        assertEquals(emptyMap<String, Long>(), HiddenSeriesCodec.decode(null))
    }
}
