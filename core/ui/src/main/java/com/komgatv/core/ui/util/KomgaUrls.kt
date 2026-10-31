package com.komgatv.core.ui.util

/**
 * Komga 절대 이미지 URL 생성 유틸리티
 *
 * 세션의 기본 URL(baseUrl)을 기준으로 썸네일 및 페이지 이미지의 절대 URL을 생성합니다.
 */
object KomgaUrls {

    /**
     * 시리즈 표지 썸네일 절대 URL 반환
     */
    fun seriesThumbnail(baseUrl: String, seriesId: String): String {
        if (baseUrl.isBlank() || seriesId.isBlank()) return ""
        return "${baseUrl.trimEnd('/')}/api/v1/series/$seriesId/thumbnail"
    }

    /**
     * 책 표지 썸네일 절대 URL 반환
     */
    fun bookThumbnail(baseUrl: String, bookId: String): String {
        if (baseUrl.isBlank() || bookId.isBlank()) return ""
        return "${baseUrl.trimEnd('/')}/api/v1/books/$bookId/thumbnail"
    }

    /**
     * 책 페이지 이미지 절대 URL 반환 (pageNumber는 1부터 시작)
     */
    fun bookPage(baseUrl: String, bookId: String, pageNumber: Int): String {
        if (baseUrl.isBlank() || bookId.isBlank()) return ""
        return "${baseUrl.trimEnd('/')}/api/v1/books/$bookId/pages/$pageNumber"
    }
}
