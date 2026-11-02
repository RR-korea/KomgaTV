package com.komgatv.feature.reader.continuous

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.komgatv.core.data.cache.PageFileCache
import com.komgatv.feature.reader.enhance.ImageEnhancer
import com.komgatv.feature.reader.enhance.lanczosScaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * 세로 스크롤 리더의 타일 디코더.
 *
 * 페이지 파일에서 타일 영역만 [BitmapRegionDecoder]로, 표시 너비 이상이 되는 가장 큰 `inSampleSize`와 RGB_565로 디코딩한다.
 * 디코딩한 타일은 메모리 상한이 있는 LRU에 두고, 영역 디코더는 최근 몇 쪽만 열어 둔다.
 */
class TileDecoder @Inject constructor(
    private val pageFileCache: PageFileCache,
) : TileSource {
    private val lock = Any()

    private val decoders = object : LruCache<String, BitmapRegionDecoder>(OPEN_DECODERS) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: BitmapRegionDecoder, newValue: BitmapRegionDecoder?) {
            oldValue.recycle()
        }
    }

    private val bitmaps = object : LruCache<String, ImageBitmap>(BITMAP_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount
    }

    override suspend fun tile(bookId: String, tile: PageTile, sharpen: Float?, lanczos: Int?): ImageBitmap {
        val key = "$bookId/${tile.key}/${tile.displayWidth}/${sharpen ?: 0f}/${lanczos ?: 0}"
        bitmaps.get(key)?.let { return it }
        val file = pageFileCache.getPageFile(bookId, tile.pageNumber)
        val bitmap = withContext(Dispatchers.Default) {
            // 영역 디코더를 쓰는 동안 닫히면 안 되므로 디코딩과 정리는 같은 잠금 안에서 한다
            var decoded = synchronized(lock) { decode(file, tile) }
            // Lanczos·선명하게는 잠금 밖에서 (다른 타일 디코딩을 막지 않게)
            if (lanczos != null && (decoded.width != tile.displayWidth || decoded.height != tile.displayHeight)) {
                decoded = decoded.lanczosScaled(tile.displayWidth, tile.displayHeight, lanczos).also { decoded.recycle() }
            }
            sharpen?.let { amount -> ImageEnhancer.sharpen(decoded, amount).also { decoded.recycle() } } ?: decoded
        }.asImageBitmap()
        bitmaps.put(key, bitmap)
        return bitmap
    }

    override suspend fun bounds(bookId: String, pageNumber: Int): Pair<Int, Int>? {
        val file = pageFileCache.getPageFile(bookId, pageNumber)
        return withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
        }
    }

    override suspend fun prefetch(bookId: String, pageNumber: Int) {
        pageFileCache.getPageFile(bookId, pageNumber)
    }

    override fun clear() {
        synchronized(lock) { decoders.evictAll() }
        bitmaps.evictAll()
    }

    private fun decode(file: File, tile: PageTile): Bitmap {
        val decoder = openDecoder(file)
        val region = tile.region?.let { Rect(it.left, it.top, it.right, it.bottom) }
            ?: Rect(0, 0, decoder?.width ?: 0, decoder?.height ?: 0)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(region.width(), tile.displayWidth)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        if (decoder != null) {
            return decoder.decodeRegion(region, options) ?: throw IOException("타일 디코딩 실패: ${file.name}")
        }
        // 영역 디코딩을 못 하는 형식(GIF 등): 통째로 줄여 읽은 뒤 영역을 잘라낸다
        val whole = BitmapFactory.decodeFile(file.absolutePath, options) ?: throw IOException("디코딩 실패: ${file.name}")
        if (tile.region == null) return whole
        val s = options.inSampleSize
        val top = (region.top / s).coerceIn(0, whole.height - 1)
        val height = (region.height() / s).coerceIn(1, whole.height - top)
        return Bitmap.createBitmap(whole, 0, top, whole.width, height).also { if (it !== whole) whole.recycle() }
    }

    private fun openDecoder(file: File): BitmapRegionDecoder? {
        decoders.get(file.absolutePath)?.let { return it }
        val decoder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(file.absolutePath)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(file.absolutePath, false)
            }
        } catch (_: IOException) {
            null
        } ?: return null
        decoders.put(file.absolutePath, decoder)
        return decoder
    }

    private companion object {
        const val OPEN_DECODERS = 4
        const val BITMAP_CACHE_BYTES = 48 * 1024 * 1024
    }
}

/** 디코딩한 너비가 [targetWidth] 이상으로 남는 가장 큰 2의 거듭제곱 */
internal fun sampleSize(sourceWidth: Int, targetWidth: Int): Int {
    var sample = 1
    while (targetWidth > 0 && sourceWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}
