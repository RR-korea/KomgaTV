package com.komgatv.core.data.epub

import com.komgatv.core.network.ServerSessionManager
import okhttp3.Request
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** WebView에 돌려줄 EPUB 파일 하나 (장 XHTML, 그림, CSS) */
class EpubResource(
    val mimeType: String,
    val encoding: String?,
    val statusCode: Int,
    val body: InputStream,
)

/**
 * EPUB 리더 WebView가 요청하는 파일을 세션(인증 헤더 포함)으로 받아 온다.
 * WebView는 인증 헤더를 붙일 수 없어서 요청을 가로채 이걸로 대신 받는다. WebView의 작업 스레드에서 불린다(블로킹).
 */
@Singleton
class EpubResourceLoader @Inject constructor(
    private val sessionManager: ServerSessionManager,
) {
    /** 연결된 서버의 주소면 받아 오고, 다른 주소(또는 연결 없음)면 null — WebView가 직접 처리한다 */
    fun fetch(url: String): EpubResource? {
        val session = sessionManager.session.value ?: return null
        if (!url.startsWith(session.baseUrl)) return null
        val response = session.okHttpClient.newCall(Request.Builder().url(url).build()).execute()
        val contentType = response.body.contentType()
        return EpubResource(
            mimeType = contentType?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
            encoding = contentType?.charset()?.name(),
            statusCode = response.code,
            // 스트림을 다 읽으면 WebView가 닫는다 — 응답도 함께 닫힌다
            body = response.body.byteStream(),
        )
    }
}
