package com.komgatv.feature.reader.enhance

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 칸(패널) 사이 위아래 여백을 없애고, 남은 칸들을 쪽 가운데(세로)로 모은다. 좌우 여백은 그대로.
 * 쪽 크기는 바꾸지 않는다 — 비는 위아래는 바탕색으로 채운다 (확대와 함께 쓰면 칸이 화면을 더 채운다).
 *
 * 칸 줄과 여백 줄 구분: 세로로 길게 이어지는 잉크(칸 테두리·그림)가 지나가는 줄은 칸, 아니면 여백.
 * 여백에 있는 글자(쪽 번호·역자 주·작은 효과음)는 획이 짧아 칸으로 치지 않는다 — 여백과 함께 지운다.
 * 쪽 왼쪽·오른쪽 끝(스캔 테두리 검은 줄 등)은 보지 않는다 — 위아래로 쭉 이어져 모든 줄을 칸으로 만든다.
 *
 * @return 바꾼 픽셀, 지울 여백이 거의 없으면 null
 */
internal fun collapseGutters(pixels: IntArray, width: Int, height: Int): IntArray? {
    if (width < 8 || height < 32) return null
    val lum = IntArray(pixels.size) { i ->
        val c = pixels[i]
        (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
    }
    val bg = backgroundLuminance(lum)

    // 가로를 몇 칸씩 묶어 본다 — 묶음 안에 잉크가 하나라도 있으면 잉크 (가는 테두리를 놓치지 않게)
    val step = (width / 320).coerceAtLeast(1)
    val cols = (width + step - 1) / step
    val edgeCols = ((width * EDGE_RATIO).roundToInt() + step - 1) / step
    val minRun = (height * TALL_INK_RATIO).roundToInt().coerceAtLeast(8)
    val contentRow = BooleanArray(height)
    for (col in edgeCols until cols - edgeCols) {
        val x0 = col * step
        val x1 = minOf(width, x0 + step)
        var runStart = -1
        for (y in 0..height) {
            val ink = y < height && (x0 until x1).any { x -> abs(lum[y * width + x] - bg) > INK_DIFF }
            if (ink) {
                if (runStart < 0) runStart = y
            } else if (runStart >= 0) {
                if (y - runStart >= minRun) for (r in runStart until y) contentRow[r] = true
                runStart = -1
            }
        }
    }

    // 칸 줄을 위에서부터 차례로 쌓는다. 칸 사이 여백은 얇게(테두리가 붙지 않을 만큼) 남긴다
    val minGap = (height * MIN_GAP_RATIO).roundToInt().coerceAtLeast(2)
    val keepGap = (height * KEEP_GAP_RATIO).roundToInt().coerceAtLeast(1)
    val kept = ArrayList<Int>(height) // 원래 줄 번호, -1 = 바탕색 줄
    var y = 0
    while (y < height) {
        if (contentRow[y]) {
            kept += y
            y++
            continue
        }
        val start = y
        while (y < height && !contentRow[y]) y++
        val isEdge = start == 0 || y == height
        when {
            isEdge -> Unit // 쪽 위아래 여백은 다 지운다 (가운데로 모을 때 다시 생긴다)
            y - start >= minGap -> repeat(keepGap) { kept += -1 }
            else -> for (r in start until y) kept += r // 칸 안의 짧은 빈 줄은 그대로
        }
    }
    if (kept.isEmpty() || height - kept.size < height * MIN_SAVED_RATIO) return null

    val fill = (0xFF shl 24) or (bg shl 16) or (bg shl 8) or bg
    val out = IntArray(pixels.size) { fill }
    val top = (height - kept.size) / 2
    kept.forEachIndexed { i, src ->
        if (src >= 0) System.arraycopy(pixels, src * width, out, (top + i) * width, width)
    }
    return out
}

/** 바탕 밝기 — 가장 흔한 밝기 (흰 종이면 255 근처, 검은 바탕 쪽이면 0 근처) */
private fun backgroundLuminance(lum: IntArray): Int {
    val hist = IntArray(256)
    for (i in lum.indices step 3) hist[lum[i]]++
    return hist.indices.maxBy { hist[it] }
}

/** 왼쪽·오른쪽 끝에서 이만큼은 보지 않는다 (스캔 테두리) */
private const val EDGE_RATIO = 0.02f

/** 바탕과 이만큼 다르면 잉크 */
private const val INK_DIFF = 80

/** 세로로 쪽 높이의 이만큼 이어지는 잉크가 있어야 칸 — 여백의 글자는 이보다 짧다 */
private const val TALL_INK_RATIO = 0.04f

/** 이보다 짧은 빈 줄은 칸 안의 빈 곳으로 보고 둔다 */
private const val MIN_GAP_RATIO = 0.006f

/** 칸 사이에 남기는 여백 */
private const val KEEP_GAP_RATIO = 0.003f

/** 지울 여백이 이보다 적으면 그대로 */
private const val MIN_SAVED_RATIO = 0.005f

/** 페이지 리더(Coil)용 — 작품 보기 설정 "칸 간격 없애기" */
internal class GutterTransformation : Transformation() {
    override val cacheKey: String = "gutters-2"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = collapseGutters(pixels, w, h) ?: return input
        val config = if (input.config == Bitmap.Config.RGB_565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        return createBitmap(w, h, config).apply { setPixels(out, 0, w, 0, 0, w, h) }
    }
}
