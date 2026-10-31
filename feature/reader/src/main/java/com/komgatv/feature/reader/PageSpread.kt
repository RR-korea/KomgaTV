package com.komgatv.feature.reader

import com.komgatv.core.model.BookPage
import com.komgatv.core.model.PageLayout

/**
 * 리더에서 한 번에 표시되는 페이지 펼침 (단일 페이지 또는 두 쪽 페이지)
 *
 * @property pages 펼침에 포함된 페이지 목록 (1장 또는 2장)
 */
data class PageSpread(
    val pages: List<BookPage>,
) {
    /** 펼침에 포함된 페이지 번호 중 가장 큰 번호 (진행도 저장용) */
    val highestPageNumber: Int
        get() = pages.maxOfOrNull { it.number } ?: 0

    /** 특정 페이지 번호가 이 펼침에 포함되어 있는지 여부 */
    fun containsPage(pageNumber: Int): Boolean = pages.any { it.number == pageNumber }
}

/**
 * Komelia(Apache-2.0)의 PagedReaderState.kt 펼침 분할 알고리즘을 이식한 빌더입니다.
 *
 * Source: https://github.com/Snd-R/Komelia
 * File: komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/paged/PagedReaderState.kt
 * License: Apache License 2.0
 */
object SpreadMapBuilder {

    private data class DoublePageSegment(
        val singlePages: List<BookPage>,
        val landscapePage: BookPage?,
    )

    /**
     * 책의 페이지 목록과 레이아웃 설정, 오프셋(밀기) 여부를 바탕으로 펼침 목록을 생성합니다.
     */
    fun buildSpreadMap(
        pages: List<BookPage>,
        layout: PageLayout,
        offset: Boolean = false,
    ): List<PageSpread> {
        if (pages.isEmpty()) return emptyList()

        return when (layout) {
            PageLayout.SINGLE_PAGE -> pages.map { PageSpread(listOf(it)) }
            PageLayout.DOUBLE_PAGES -> buildDoublePageSpreads(pages, withCover = true, offset = offset)
            PageLayout.DOUBLE_PAGES_NO_COVER -> buildDoublePageSpreads(pages, withCover = false, offset = offset)
        }
    }

    private fun buildDoublePageSpreads(
        pages: List<BookPage>,
        withCover: Boolean,
        offset: Boolean,
    ): List<PageSpread> {
        val segments = constructDoublePageSegments(pages, withCover)
        val processedSpreads = mutableListOf<PageSpread>()

        for (segment in segments) {
            val segmentPages = segment.singlePages

            if (offset) {
                segmentPages.firstOrNull()?.let { processedSpreads.add(PageSpread(listOf(it))) }
                processedSpreads.addAll(segmentPages.drop(1).chunked(2).map { PageSpread(it) })
            } else {
                processedSpreads.addAll(segmentPages.chunked(2).map { PageSpread(it) })
            }

            segment.landscapePage?.let { processedSpreads.add(PageSpread(listOf(it))) }
        }

        return processedSpreads
    }

    private fun constructDoublePageSegments(
        pages: List<BookPage>,
        withCover: Boolean,
    ): List<DoublePageSegment> {
        val independentSegments = mutableListOf<DoublePageSegment>()

        val pagesToProcess = if (withCover) {
            if (pages.isNotEmpty()) {
                independentSegments.add(DoublePageSegment(listOf(pages.first()), null))
                pages.drop(1)
            } else {
                emptyList()
            }
        } else {
            pages
        }

        var segmentPages = mutableListOf<BookPage>()
        for (page in pagesToProcess) {
            if (page.isLandscape()) {
                independentSegments.add(DoublePageSegment(segmentPages, page))
                segmentPages = mutableListOf()
            } else {
                segmentPages.add(page)
            }
        }
        if (segmentPages.isNotEmpty()) {
            independentSegments.add(DoublePageSegment(segmentPages, null))
        }

        return independentSegments
    }
}
