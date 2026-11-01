package com.komgatv.feature.reader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.bitmapConfig
import coil3.size.Dimension
import coil3.size.Size
import kotlinx.coroutines.runInterruptible
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Komga의 PDF 한 쪽(`/pages/{n}/raw`, application/pdf)을 TV에서 바로 그린다.
 *
 * 서버가 쪽마다 그림으로 그려 보내면(PDFBox 렌더링 + 이미지 인코딩) NAS에서 2~3초가 걸린다.
 * 쪽만 잘라 받으면 서버는 복사만 하고, 그리기는 안드로이드에 들어 있는 PDF 엔진이 화면 크기에 맞춰 한다.
 */
class PdfPageDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult = runInterruptible {
        val file = source.fileOrNull()?.toFile() ?: copyToTemp()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val (width, height) = targetSize(page.width, page.height, options.size)
                        val rendered = createBitmap(width, height)
                        rendered.eraseColor(Color.WHITE)
                        page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        // 앱은 메모리를 아끼려 RGB_565로 둔다 (PDF 엔진은 ARGB_8888에만 그린다)
                        val bitmap = if (options.bitmapConfig == Bitmap.Config.RGB_565) {
                            rendered.copy(Bitmap.Config.RGB_565, false).also { rendered.recycle() }
                        } else {
                            rendered
                        }
                        DecodeResult(image = bitmap.asImage(), isSampled = true)
                    }
                }
            }
        } finally {
            if (file.name.startsWith(TEMP_PREFIX)) file.delete()
        }
    }

    /** PdfRenderer는 파일이 있어야 한다 — 디스크 캐시에 없으면 임시 파일로 */
    private fun copyToTemp(): File {
        val temp = File.createTempFile(TEMP_PREFIX, ".pdf", options.context.cacheDir)
        temp.outputStream().use { out -> source.source().inputStream().use { it.copyTo(out) } }
        return temp
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            if (result.mimeType != PDF_MIME_TYPE) return null
            return PdfPageDecoder(result.source, options)
        }
    }

    companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        private const val TEMP_PREFIX = "pdfpage"

        /** 그릴 크기가 정해지지 않았을 때 긴 변 */
        private const val DEFAULT_LONG_SIDE = 2048

        /** 싼 TV의 텍스처 한도 */
        private const val MAX_SIDE = 4096

        /**
         * 쪽(포인트 단위)을 요청 크기 안에 꼭 맞게 — 비율 유지, 긴 변은 [MAX_SIDE] 이하.
         */
        fun targetSize(pageWidth: Int, pageHeight: Int, size: Size): Pair<Int, Int> {
            val w = pageWidth.coerceAtLeast(1).toFloat()
            val h = pageHeight.coerceAtLeast(1).toFloat()
            val maxW = (size.width as? Dimension.Pixels)?.px
            val maxH = (size.height as? Dimension.Pixels)?.px
            var scale = when {
                maxW != null && maxH != null -> min(maxW / w, maxH / h)
                maxW != null -> maxW / w
                maxH != null -> maxH / h
                else -> DEFAULT_LONG_SIDE / maxOf(w, h)
            }
            scale = min(scale, MAX_SIDE / maxOf(w, h))
            return (w * scale).roundToInt().coerceAtLeast(1) to (h * scale).roundToInt().coerceAtLeast(1)
        }
    }
}
