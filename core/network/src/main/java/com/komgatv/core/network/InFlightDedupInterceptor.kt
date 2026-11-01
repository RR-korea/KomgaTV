package com.komgatv.core.network

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 같은 주소를 동시에 받으면 한 번만 받는다 (이미지 전용).
 *
 * 리더는 쪽을 미리 받는 요청과 화면에 그리는 요청이 거의 동시에 같은 쪽을 부른다. Coil은 진행 중인 요청을 합치지 않아
 * 서버가 같은 쪽을 두 번 보낸다 — PDF는 서버가 쪽마다 그림을 새로 그려서(2~3초) 특히 비싸다.
 * 먼저 온 요청이 받는 동안 뒤 요청은 기다렸다가 같은 내용을 받는다. 먼저 온 요청이 실패하거나 취소되면 뒤 요청이 직접 받는다.
 */
class InFlightDedupInterceptor : Interceptor {

    private class Captured(
        val protocol: Protocol,
        val code: Int,
        val message: String,
        val headers: Headers,
        val contentType: MediaType?,
        val bytes: ByteArray,
    ) {
        fun toResponse(request: Request): Response = Response.Builder()
            .request(request)
            .protocol(protocol)
            .code(code)
            .message(message)
            .headers(headers)
            .body(bytes.toResponseBody(contentType))
            .build()
    }

    private class InFlight {
        val done = CountDownLatch(1)

        @Volatile
        var captured: Captured? = null
    }

    private val inFlight = ConcurrentHashMap<String, InFlight>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" || request.header("Range") != null) return chain.proceed(request)

        val key = request.url.toString()
        val mine = InFlight()
        val leader = inFlight.putIfAbsent(key, mine)
        if (leader != null) {
            // 앞 요청을 기다린다 — 이 요청이 취소되면 바로 그만둔다
            while (!leader.done.await(WAIT_SLICE_MILLIS, TimeUnit.MILLISECONDS)) {
                if (chain.call().isCanceled()) throw IOException("Canceled")
            }
            return leader.captured?.toResponse(request) ?: chain.proceed(request)
        }

        try {
            val response = chain.proceed(request)
            if (!response.isSuccessful) return response
            val captured = response.use {
                Captured(it.protocol, it.code, it.message, it.headers, it.body.contentType(), it.body.bytes())
            }
            mine.captured = captured
            return captured.toResponse(request)
        } finally {
            inFlight.remove(key, mine)
            mine.done.countDown()
        }
    }

    private companion object {
        const val WAIT_SLICE_MILLIS = 100L
    }
}
