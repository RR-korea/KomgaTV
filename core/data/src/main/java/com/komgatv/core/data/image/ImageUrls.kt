package com.komgatv.core.data.image

import com.komgatv.core.network.ServerSessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Komga 이미지 절대 URL
 *
 * 화면은 서버 주소를 몰라도 되고, Coil이 세션의 OkHttpClient로 인증 헤더를 붙여 받는다.
 * 연결된 서버가 없으면 null.
 */
interface ImageUrls {
    fun seriesThumbnail(seriesId: String): String?
    fun bookThumbnail(bookId: String): String?

    /** pageNumber는 1부터 시작한다. */
    fun page(bookId: String, pageNumber: Int): String?
}

@Singleton
class SessionImageUrls @Inject constructor(
    private val sessionManager: ServerSessionManager,
) : ImageUrls {

    override fun seriesThumbnail(seriesId: String) = url("api/v1/series/$seriesId/thumbnail")

    override fun bookThumbnail(bookId: String) = url("api/v1/books/$bookId/thumbnail")

    override fun page(bookId: String, pageNumber: Int) = url("api/v1/books/$bookId/pages/$pageNumber")

    private fun url(path: String): String? =
        sessionManager.session.value?.baseUrl?.let { "$it/$path" }
}
