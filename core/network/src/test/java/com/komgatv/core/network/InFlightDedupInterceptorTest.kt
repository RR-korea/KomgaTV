package com.komgatv.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InFlightDedupInterceptorTest {

    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().addInterceptor(InFlightDedupInterceptor()).build()
    private val pool = Executors.newFixedThreadPool(3)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        pool.shutdownNow()
        server.shutdown()
    }

    private fun get(path: String) = Callable {
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().use { it.code to it.body.string() }
    }

    @Test
    fun `같은 주소를 동시에 부르면 서버에는 한 번만 가고 둘 다 같은 내용을 받는다`() {
        server.enqueue(MockResponse().setBody("page-1").setHeadersDelay(300, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setBody("second"))

        val first = pool.submit(get("/pages/1"))
        Thread.sleep(50)
        val second = pool.submit(get("/pages/1"))

        assertEquals(200 to "page-1", first.get(5, TimeUnit.SECONDS))
        assertEquals(200 to "page-1", second.get(5, TimeUnit.SECONDS))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `다른 주소는 따로 받는다`() {
        server.enqueue(MockResponse().setBody("a").setHeadersDelay(200, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setBody("b"))

        val a = pool.submit(get("/pages/1"))
        val b = pool.submit(get("/pages/2"))

        // 서버는 받은 순서대로 응답한다 — 어느 쪽이 먼저든 둘 다 서버까지 간다
        val bodies = setOf(a.get(5, TimeUnit.SECONDS).second, b.get(5, TimeUnit.SECONDS).second)
        assertEquals(setOf("a", "b"), bodies)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `앞 요청이 실패하면 기다리던 요청은 직접 받는다`() {
        server.enqueue(MockResponse().setResponseCode(500).setHeadersDelay(300, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setBody("retry"))

        val first = pool.submit(get("/pages/1"))
        Thread.sleep(50)
        val second = pool.submit(get("/pages/1"))

        assertEquals(500, first.get(5, TimeUnit.SECONDS).first)
        assertEquals(200 to "retry", second.get(5, TimeUnit.SECONDS))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `끝난 뒤 다시 부르면 새로 받는다 (합치는 건 진행 중일 때만)`() {
        server.enqueue(MockResponse().setBody("v1"))
        server.enqueue(MockResponse().setBody("v2"))

        assertEquals("v1", get("/pages/1").call().second)
        assertEquals("v2", get("/pages/1").call().second)
    }
}
