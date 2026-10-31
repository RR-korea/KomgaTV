package com.komgatv.core.data.error

import com.komgatv.core.data.repository.NotConnectedException
import com.komgatv.core.model.LoadError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException

class LoadErrorsTest {

    @Test
    fun `세션 없음은 NOT_CONNECTED`() {
        assertEquals(LoadError.NOT_CONNECTED, NotConnectedException().toLoadError())
    }

    @Test
    fun `원인 사슬 안의 네트워크 오류는 UNREACHABLE`() {
        val e = IllegalStateException("wrapped", ConnectException("refused"))
        assertEquals(LoadError.UNREACHABLE, e.toLoadError())
    }

    @Test
    fun `그 밖의 오류는 UNKNOWN`() {
        assertEquals(LoadError.UNKNOWN, RuntimeException("boom").toLoadError())
    }
}
