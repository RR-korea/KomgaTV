package com.komgatv.feature.reader.enhance

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.size.Dimension
import coil3.size.Size
import coil3.transform.Transformation
import com.komgatv.core.model.ScaleType
import java.util.stream.IntStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lanczos 창 크기 [a] — 양쪽 a픽셀씩(2a탭). 2는 빠르고 부드럽고, 3은 흔한 절충, 4는 더 또렷하지만 느리다.
 */
private fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

private fun lanczos(x: Double, a: Int): Double = if (abs(x) < a) sinc(x) * sinc(x / a) else 0.0

/** 한 방향으로 다시 맞출 때 출력 픽셀마다 쓰는 원본 픽셀과 가중치 */
private class Kernel(val start: IntArray, val weights: Array<DoubleArray>)

private fun kernel(src: Int, dst: Int, a: Int): Kernel {
    val scale = dst.toDouble() / src
    // 줄일 때는 창을 넓혀 원본을 고르게 섞는다 (안 그러면 계단·물결이 생긴다)
    val support = if (scale < 1) a / scale else a.toDouble()
    val stretch = if (scale < 1) scale else 1.0
    val start = IntArray(dst)
    val weights = Array(dst) { i ->
        val center = (i + 0.5) / scale - 0.5
        val from = max(0, floor(center - support + 1).toInt())
        val to = min(src - 1, ceil(center + support - 1).toInt())
        start[i] = from
        val w = DoubleArray(to - from + 1) { k -> lanczos((from + k - center) * stretch, a) }
        val sum = w.sum()
        if (sum != 0.0) for (k in w.indices) w[k] /= sum
        w
    }
    return Kernel(start, weights)
}

private fun clamp(v: Double): Int = v.roundToInt().coerceIn(0, 255)

/**
 * Lanczos로 크기를 바꾼다 (가로 → 세로 두 번, 창 크기 [a]). 픽셀은 ARGB 정수, 알파도 같은 방법으로.
 * 쌍선형보다 윤곽이 또렷하고 계단이 적다 — 만화 선화에 알맞다.
 */
internal fun lanczosResize(pixels: IntArray, width: Int, height: Int, newWidth: Int, newHeight: Int, a: Int = 3): IntArray {
    if (width == newWidth && height == newHeight) return pixels.copyOf()
    val kx = kernel(width, newWidth, a)
    val ky = kernel(height, newHeight, a)
    // 1단계: 가로 (채널별 실수로 둔다). 줄마다 따로라 여러 코어로 나눠 한다
    val tmp = Array(4) { DoubleArray(newWidth * height) }
    parallelRows(height) { y ->
        val row = y * width
        for (x in 0 until newWidth) {
            val w = kx.weights[x]
            val s = kx.start[x]
            var a = 0.0
            var r = 0.0
            var g = 0.0
            var b = 0.0
            for (k in w.indices) {
                val c = pixels[row + s + k]
                val wk = w[k]
                a += ((c ushr 24) and 0xFF) * wk
                r += ((c shr 16) and 0xFF) * wk
                g += ((c shr 8) and 0xFF) * wk
                b += (c and 0xFF) * wk
            }
            val i = y * newWidth + x
            tmp[0][i] = a
            tmp[1][i] = r
            tmp[2][i] = g
            tmp[3][i] = b
        }
    }
    // 2단계: 세로
    val out = IntArray(newWidth * newHeight)
    parallelRows(newHeight) { y ->
        val w = ky.weights[y]
        val s = ky.start[y]
        for (x in 0 until newWidth) {
            var a = 0.0
            var r = 0.0
            var g = 0.0
            var b = 0.0
            for (k in w.indices) {
                val i = (s + k) * newWidth + x
                val wk = w[k]
                a += tmp[0][i] * wk
                r += tmp[1][i] * wk
                g += tmp[2][i] * wk
                b += tmp[3][i] * wk
            }
            out[y * newWidth + x] = (clamp(a) shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
        }
    }
    return out
}

/** 줄 [0, rows)을 여러 코어에 나눠 처리한다 (쪽 하나도 코어 수만큼 빨라진다) */
private inline fun parallelRows(rows: Int, crossinline body: (Int) -> Unit) {
    IntStream.range(0, rows).parallel().forEach { body(it) }
}

/** 비트맵을 Lanczos(창 [a])로 [newWidth]×[newHeight]에 맞춘 새 비트맵 (RGB_565는 그대로) */
internal fun Bitmap.lanczosScaled(newWidth: Int, newHeight: Int, a: Int = 3): Bitmap {
    val src = IntArray(width * height)
    getPixels(src, 0, width, 0, 0, width, height)
    val out = lanczosResize(src, width, height, newWidth, newHeight, a)
    val config = if (config == Bitmap.Config.RGB_565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
    return createBitmap(newWidth, newHeight, config).apply { setPixels(out, 0, newWidth, 0, 0, newWidth, newHeight) }
}

/**
 * 크기 맞춤으로 그릴 크기를 정한다 — 화면 맞춤은 칸 안에 꼭, 너비 맞춤은 칸 너비, 높이 맞춤은 화면 높이. 원본은 그대로(null).
 */
internal fun fittedSize(width: Int, height: Int, size: Size, scaleType: ScaleType): Pair<Int, Int>? {
    val boxW = (size.width as? Dimension.Pixels)?.px ?: return null
    val boxH = (size.height as? Dimension.Pixels)?.px ?: return null
    val scale = when (scaleType) {
        ScaleType.SCREEN -> min(boxW.toDouble() / width, boxH.toDouble() / height)
        ScaleType.FIT_WIDTH -> boxW.toDouble() / width
        // 높이 맞춤 요청은 너비를 넉넉히(칸의 2배) 잡았다 — 높이만 본다
        ScaleType.FIT_HEIGHT -> boxH.toDouble() / height
        ScaleType.ORIGINAL -> return null
    }
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

/**
 * 페이지 리더(Coil)용 Lanczos — 디코딩한 쪽을 화면에 그릴 크기로 미리 맞춰, 화면은 그대로(1:1) 그린다.
 * 크기가 거의 같으면 건너뛴다.
 */
internal class LanczosTransformation(private val scaleType: ScaleType, private val a: Int) : Transformation() {
    override val cacheKey: String = "lanczos$a-$scaleType"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val (w, h) = fittedSize(input.width, input.height, size, scaleType) ?: return input
        if (abs(w - input.width) <= 1 && abs(h - input.height) <= 1) return input
        return input.lanczosScaled(w, h, a)
    }
}
