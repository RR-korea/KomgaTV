package com.komgatv.feature.reader.continuous

import com.komgatv.core.model.BookPage

/**
 * 타일의 원본 이미지 내 직사각형 영역 (순수 Kotlin 모델)
 */
data class TileRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * 세로 스크롤 리더의 한 타일 조각
 *
 * @property pageNumber 1부터 시작하는 책 페이지 번호
 * @property tileIndex 해당 페이지 내 타일 인덱스 (0-based)
 * @property totalTilesInPage 해당 페이지의 전체 타일 수
 * @property region 원본 이미지에서 잘라낼 영역 (자리표시인 경우 null)
 * @property displayWidth 화면에 표시될 너비 (px)
 * @property displayHeight 화면에 표시될 높이 (px)
 * @property bottomSpacingDp 해당 타일 아래 여백 dp (페이지의 마지막 타일이면 pageSpacing, 아니면 0)
 * @property isPlaceholder 이미지 크기를 몰라 임시로 화면 높이로 채운 자리표시 타일 여부
 */
data class PageTile(
    val pageNumber: Int,
    val tileIndex: Int,
    val totalTilesInPage: Int,
    val region: TileRegion?,
    val displayWidth: Int,
    val displayHeight: Int,
    val bottomSpacingDp: Int,
    val isPlaceholder: Boolean = false,
) {
    val key: String get() = "${pageNumber}_$tileIndex"
}

/**
 * 세로 스크롤 리더 타일 계획
 *
 * 쪽 너비 = 화면 너비 × (1 − 2 × 여백) (Komelia continuousPadding과 같은 뜻).
 * 웹툰처럼 긴 쪽은 싼 TV의 텍스처 한도(4096px)와 메모리 때문에 그렸을 때 화면 높이 이하인 조각으로 나눈다.
 */
object ContinuousTilePlanner {

    data class PlanParams(
        val screenWidthPx: Int,
        val screenHeightPx: Int,
        val sidePaddingFraction: Float = 0f, // 0.0f ~ 0.45f
        val pageSpacingDp: Int = 0,           // 0 ~ 64 dp
    )

    /**
     * 단일 페이지를 화면 및 여백 설정에 맞춰 하나 이상의 타일 목록으로 계획합니다.
     */
    fun planPageTiles(
        page: BookPage,
        params: PlanParams,
    ): List<PageTile> {
        val targetWidthPx = (params.screenWidthPx * (1f - 2f * params.sidePaddingFraction))
            .toInt()
            .coerceAtLeast(1)

        // 크기 정보가 없거나 유효하지 않은 경우: 화면 높이의 자리표시 타일 1개 생성
        val width = page.width
        val height = page.height
        if (width == null || height == null || width <= 0 || height <= 0) {
            return listOf(
                PageTile(
                    pageNumber = page.number,
                    tileIndex = 0,
                    totalTilesInPage = 1,
                    region = null,
                    displayWidth = targetWidthPx,
                    displayHeight = params.screenHeightPx.coerceAtLeast(1),
                    bottomSpacingDp = params.pageSpacingDp,
                    isPlaceholder = true,
                )
            )
        }

        // 스케일 및 표시 너비 계산
        // 작은 쪽도 늘 쪽 너비까지 키운다
        val scale = targetWidthPx.toFloat() / width.toFloat()
        val displayWidth = targetWidthPx

        // 원본 이미지 기준 타일 하나의 최대 높이 (화면 높이 이하가 되도록)
        val maxSrcTileHeight = (params.screenHeightPx / scale).toInt().coerceAtLeast(1)

        // 원본 높이가 타일 최대 높이 이하인 경우: 타일 1개
        if (height <= maxSrcTileHeight) {
            val displayHeight = (height * scale).toInt().coerceAtLeast(1)
            return listOf(
                PageTile(
                    pageNumber = page.number,
                    tileIndex = 0,
                    totalTilesInPage = 1,
                    region = TileRegion(left = 0, top = 0, right = width, bottom = height),
                    displayWidth = displayWidth,
                    displayHeight = displayHeight,
                    bottomSpacingDp = params.pageSpacingDp,
                    isPlaceholder = false,
                )
            )
        }

        // 화면보다 긴 이미지: 여러 타일로 세로 분할
        val tiles = mutableListOf<PageTile>()
        var top = 0
        var tileIndex = 0

        // 전체 타일 수 사전 계산
        val totalTiles = ((height + maxSrcTileHeight - 1) / maxSrcTileHeight).coerceAtLeast(1)

        while (top < height) {
            val bottom = (top + maxSrcTileHeight).coerceAtMost(height)
            val sliceHeight = bottom - top
            val sliceDisplayHeight = (sliceHeight * scale).toInt().coerceAtLeast(1)
            val isLastTile = bottom == height

            tiles.add(
                PageTile(
                    pageNumber = page.number,
                    tileIndex = tileIndex,
                    totalTilesInPage = totalTiles,
                    region = TileRegion(left = 0, top = top, right = width, bottom = bottom),
                    displayWidth = displayWidth,
                    displayHeight = sliceDisplayHeight,
                    bottomSpacingDp = if (isLastTile) params.pageSpacingDp else 0,
                    isPlaceholder = false,
                )
            )

            top = bottom
            tileIndex++
        }

        return tiles
    }

    /**
     * 전체 페이지 목록에 대한 타일 목록을 일괄 생성합니다.
     */
    fun planPages(
        pages: List<BookPage>,
        params: PlanParams,
    ): List<PageTile> {
        return pages.flatMap { page -> planPageTiles(page, params) }
    }
}
