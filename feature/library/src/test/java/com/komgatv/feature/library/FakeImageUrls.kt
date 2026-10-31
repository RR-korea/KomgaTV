package com.komgatv.feature.library

import com.komgatv.core.data.image.ImageUrls

/** 테스트용 고정 주소 이미지 URL */
object FakeImageUrls : ImageUrls {
    private const val BASE = "http://example.com:8080"
    override fun seriesThumbnail(seriesId: String) = "$BASE/api/v1/series/$seriesId/thumbnail"
    override fun bookThumbnail(bookId: String) = "$BASE/api/v1/books/$bookId/thumbnail"
    override fun page(bookId: String, pageNumber: Int) = "$BASE/api/v1/books/$bookId/pages/$pageNumber"
}
