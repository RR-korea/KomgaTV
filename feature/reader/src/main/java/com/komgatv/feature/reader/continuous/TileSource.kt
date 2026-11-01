package com.komgatv.feature.reader.continuous

import androidx.compose.ui.graphics.ImageBitmap

/** 세로 스크롤 리더가 타일 이미지와 쪽 크기를 얻는 곳 (구현: [TileDecoder]) */
interface TileSource {
    /** 타일 이미지 ([sharpen]이 있으면 그 세기로 선명하게). 실패하면 예외 */
    suspend fun tile(bookId: String, tile: PageTile, sharpen: Float? = null): ImageBitmap

    /** 쪽 이미지의 가로·세로 (읽지 못하면 null) */
    suspend fun bounds(bookId: String, pageNumber: Int): Pair<Int, Int>?

    /** 쪽 파일을 미리 받아 둔다 */
    suspend fun prefetch(bookId: String, pageNumber: Int)

    /** 열린 디코더와 메모리의 타일을 놓는다 */
    fun clear()
}
