package com.komgatv.feature.reader.enhance

import android.graphics.Bitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation
import com.komgatv.core.model.ImageEnhancement

/**
 * 화질 개선
 *
 * - 확대 필터: 끄기 = 기본(쌍선형), 나머지 = 고품질(바이큐빅·밉맵) — 화면에 그릴 때 Compose가 한다.
 * - 선명하게: 디코딩한 그림에 언샤프 마스크 (그림 − 흐린 그림을 더해 윤곽을 또렷하게). 흐린 스캔본에 효과가 크다.
 *   TV에서도 페이지마다 수십 ms라 페이지 넘김을 막지 않는다 (디코딩 스레드에서 한다).
 */
internal object ImageEnhancer {

    fun filterQuality(enhancement: ImageEnhancement): FilterQuality =
        if (enhancement == ImageEnhancement.OFF) FilterQuality.Low else FilterQuality.High

    /** 언샤프 마스크 세기. null이면 하지 않는다 */
    fun sharpenAmount(enhancement: ImageEnhancement): Float? = when (enhancement) {
        ImageEnhancement.SHARPEN -> 0.6f
        ImageEnhancement.SHARPEN_STRONG -> 1.2f
        else -> null
    }

    /** 그림을 선명하게 한 새 비트맵 (메모리를 아끼려 RGB_565는 그대로 둔다) */
    fun sharpen(input: Bitmap, amount: Float): Bitmap {
        val w = input.width
        val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = unsharpMask(pixels, w, h, amount)
        val config = if (input.config == Bitmap.Config.RGB_565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        return createBitmap(w, h, config).apply { setPixels(out, 0, w, 0, 0, w, h) }
    }
}

/** 페이지 리더(Coil)용 — 캐시 키에 세기가 들어가 설정을 바꾸면 다시 만든다 */
internal class SharpenTransformation(private val amount: Float) : Transformation() {
    override val cacheKey: String = "sharpen-$amount"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = ImageEnhancer.sharpen(input, amount)
}

/**
 * 언샤프 마스크: out = 그림 + amount × (그림 − 3×3 평균). 채널마다, 0~255로 자른다. 알파는 그대로.
 * 가장자리는 가장 가까운 픽셀을 늘려 쓴다.
 */
internal fun unsharpMask(pixels: IntArray, width: Int, height: Int, amount: Float): IntArray {
    if (width < 1 || height < 1) return pixels.copyOf()
    // 가로 3칸 합 → 세로 3칸 합 = 3×3 합 (채널별)
    val rowR = IntArray(pixels.size)
    val rowG = IntArray(pixels.size)
    val rowB = IntArray(pixels.size)
    for (y in 0 until height) {
        val base = y * width
        for (x in 0 until width) {
            var r = 0
            var g = 0
            var b = 0
            for (dx in -1..1) {
                val c = pixels[base + (x + dx).coerceIn(0, width - 1)]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            rowR[base + x] = r
            rowG[base + x] = g
            rowB[base + x] = b
        }
    }
    val out = IntArray(pixels.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var r = 0
            var g = 0
            var b = 0
            for (dy in -1..1) {
                val i = (y + dy).coerceIn(0, height - 1) * width + x
                r += rowR[i]
                g += rowG[i]
                b += rowB[i]
            }
            val i = y * width + x
            val c = pixels[i]
            out[i] = (c and 0xFF000000.toInt()) or
                (sharpenChannel((c shr 16) and 0xFF, r, amount) shl 16) or
                (sharpenChannel((c shr 8) and 0xFF, g, amount) shl 8) or
                sharpenChannel(c and 0xFF, b, amount)
        }
    }
    return out
}

private fun sharpenChannel(value: Int, sum9: Int, amount: Float): Int =
    (value + amount * (value - sum9 / 9f)).toInt().coerceIn(0, 255)
