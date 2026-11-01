package com.komgatv.feature.reader.translate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import java.io.File

/**
 * 글자 인식에 쓸 원본 쪽 그림.
 *
 * 화면 크기(높이 1080)로 줄인 그림은 일본어 세로쓰기를 잘 못 읽는다(雨→爾, 一緒→|緒). 같은 쪽을 높이 약 2200px로
 * 읽으면 거의 맞게 읽는다 — 이미 받아 둔 원본 파일(이미지 디스크 캐시)을 그 크기로 다시 디코딩한다.
 */
internal object OcrSource {

    /** 인식에 알맞은 쪽 높이 */
    const val TARGET_HEIGHT = 2200

    /** 원본이 캐시에 없거나 읽지 못하면 null — 화면용 그림을 키워 쓴다 */
    fun load(context: Context, url: String): Bitmap? {
        val cache = SingletonImageLoader.get(context).diskCache ?: return null
        return cache.openSnapshot(url)?.use { snapshot ->
            val file = snapshot.data.toFile()
            runCatching { if (isPdf(file)) renderPdf(file) else decodeImage(file) }.getOrNull()
        }
    }

    private fun isPdf(file: File): Boolean =
        file.inputStream().use { input -> ByteArray(4).also { input.read(it) } }.contentEquals("%PDF".toByteArray())

    private fun decodeImage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outHeight <= 0) return null
        // 메모리를 아끼려 목표보다 크면 2의 배수로 줄여 읽는다
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= TARGET_HEIGHT) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null
        return toTargetHeight(decoded)
    }

    private fun renderPdf(file: File): Bitmap =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    val height = TARGET_HEIGHT
                    val width = (page.width.toFloat() * height / page.height).toInt().coerceAtLeast(1)
                    createBitmap(width, height).apply {
                        eraseColor(Color.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    /** 높이를 목표에 맞춘다 (키우는 건 2배까지) */
    fun toTargetHeight(bitmap: Bitmap): Bitmap {
        val factor = (TARGET_HEIGHT / bitmap.height.toFloat()).coerceAtMost(2f)
        if (factor in 0.95f..1.05f) return bitmap
        val scaled = bitmap.scale((bitmap.width * factor).toInt().coerceAtLeast(1), (bitmap.height * factor).toInt())
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
