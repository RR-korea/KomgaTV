package com.komgatv.feature.reader.enhance

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 칸(패널) 사이 위아래 여백을 거의 붙게 줄이고, 칸들을 쪽 가운데(세로)로 모은다. 좌우 여백은 그대로.
 * 쪽 크기는 바꾸지 않는다 — 비는 곳은 바탕색으로 채운다 (확대와 함께 쓰면 칸이 화면을 더 채운다).
 *
 * 1. 쪽 전체 폭을 가로지르는 여백으로 쪽을 가로 띠로 나누고, 띠 사이를 붙여 쪽 가운데로 모은다.
 * 2. 띠 안이 세로 칸 묶음 여럿이면(옆에 세로로 긴 칸이 있는 작은 칸들) 묶음마다 위아래 여백을 붙이고
 *    쪽 가운데 쪽으로 민다 — 위쪽 띠는 아래로, 아래쪽 띠는 위로. 비는 곳은 쪽 바깥쪽에 생긴다.
 *
 * 칸과 여백 구분: 세로로 길게 이어지는 잉크(칸 옆 테두리·그림)가 지나가는 줄은 칸, 아니면 여백.
 * 띠를 세로 묶음으로 나눌 때는 반대로 가로로 길게 이어지는 잉크(칸 위아래 테두리)가 지나가는 열이 칸.
 * 여백에 있는 글자(쪽 번호·역자 주·작은 효과음)는 획이 짧아 칸으로 치지 않는다 — 여백과 함께 지운다.
 * 쪽 왼쪽·오른쪽 끝(스캔 테두리 검은 줄 등)은 보지 않는다 — 위아래로 쭉 이어져 모든 줄을 칸으로 만든다.
 *
 * @return 바꾼 픽셀, 바꿀 것이 거의 없으면 null
 */
internal fun collapseGutters(pixels: IntArray, width: Int, height: Int): IntArray? {
    if (width < 8 || height < 32) return null
    val lum = IntArray(pixels.size) { i ->
        val c = pixels[i]
        (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
    }
    val bg = backgroundLuminance(lum)
    val ink = BooleanArray(pixels.size) { abs(lum[it] - bg) > INK_DIFF }
    val edgeX = (width * EDGE_RATIO).roundToInt()
    val minRunV = (height * LONG_INK_RATIO).roundToInt().coerceAtLeast(8)
    val minRunH = (width * LONG_INK_RATIO).roundToInt().coerceAtLeast(8)
    val tall = longRuns(ink, width, height, vertical = true, minRun = minRunV, skipEdge = edgeX)
    val wide = longRuns(ink, width, height, vertical = false, minRun = minRunH, skipEdge = 0)

    val minGap = (height * MIN_GAP_RATIO).roundToInt().coerceAtLeast(2)
    val keepGap = (height * KEEP_GAP_RATIO).roundToInt().coerceIn(1, minGap - 1)
    val minGapX = (width * MIN_GAP_RATIO).roundToInt().coerceAtLeast(2)
    val fill = (0xFF shl 24) or (bg shl 16) or (bg shl 8) or bg

    val bands = segments(height, minGap) { y -> anyInRow(tall, width, y, 0, width) }
    if (bands.isEmpty()) return null
    var columnsMoved = false
    val blocks = bands.map { band ->
        val bandH = band.last - band.first + 1
        val block = pixels.copyOfRange(band.first * width, (band.last + 1) * width)
        val columns = segments(width, minGapX) { x -> band.any { y -> wide[y * width + x] } }
        if (columns.size < 2) return@map block
        for (col in columns) {
            val x0 = col.first
            val span = col.last - col.first + 1
            val parts = segments(bandH, minGap) { i -> anyInRow(tall, width, band.first + i, x0, x0 + span) }
            if (parts.isEmpty()) continue
            val rows = stack(parts, keepGap)
            if (rows.size >= bandH) continue
            // 쪽 가운데 쪽으로 — 위쪽 띠는 아래로, 아래쪽 띠는 위로, 가운데에 걸치면 가운데
            val top = when {
                band.last < height / 2 -> bandH - rows.size
                band.first > height / 2 -> 0
                else -> (bandH - rows.size) / 2
            }
            for (i in 0 until bandH) block.fill(fill, i * width + x0, i * width + x0 + span)
            rows.forEachIndexed { i, r ->
                if (r >= 0) System.arraycopy(pixels, (band.first + r) * width + x0, block, (top + i) * width + x0, span)
            }
            columnsMoved = true
        }
        block
    }

    val total = blocks.sumOf { it.size / width } + keepGap * (blocks.size - 1)
    if (!columnsMoved && height - total < height * MIN_SAVED_RATIO) return null
    val out = IntArray(pixels.size) { fill }
    var y = (height - total) / 2
    blocks.forEach { block ->
        System.arraycopy(block, 0, out, y * width, block.size)
        y += block.size / width + keepGap
    }
    return out
}

/** 세로(또는 가로)로 [minRun] 이상 이어지는 잉크만 남긴 표. [skipEdge]만큼 왼쪽·오른쪽 끝 열은 뺀다 (세로일 때) */
private fun longRuns(ink: BooleanArray, width: Int, height: Int, vertical: Boolean, minRun: Int, skipEdge: Int): BooleanArray {
    val out = BooleanArray(ink.size)
    val lines = if (vertical) skipEdge until width - skipEdge else 0 until height
    val length = if (vertical) height else width
    for (line in lines) {
        fun index(i: Int) = if (vertical) i * width + line else line * width + i
        var start = -1
        for (i in 0..length) {
            if (i < length && ink[index(i)]) {
                if (start < 0) start = i
            } else if (start >= 0) {
                if (i - start >= minRun) for (k in start until i) out[index(k)] = true
                start = -1
            }
        }
    }
    return out
}

private fun anyInRow(mask: BooleanArray, width: Int, y: Int, x0: Int, x1: Int): Boolean {
    val base = y * width
    for (x in x0 until x1) if (mask[base + x]) return true
    return false
}

/** 내용이 있는 구간들 — [minGap]보다 짧은 빈 곳은 구간 안으로 치고, 앞뒤 빈 곳은 뺀다 */
private fun segments(length: Int, minGap: Int, isContent: (Int) -> Boolean): List<IntRange> {
    val out = ArrayList<IntRange>()
    var start = -1
    var lastContent = -1
    for (i in 0 until length) {
        if (!isContent(i)) continue
        if (start < 0) {
            start = i
        } else if (i - lastContent - 1 >= minGap) {
            out += start..lastContent
            start = i
        }
        lastContent = i
    }
    if (start >= 0) out += start..lastContent
    return out
}

/** 구간들을 [gap]줄 사이를 두고 쌓은 원래 줄 번호 (-1 = 바탕색 줄) */
private fun stack(parts: List<IntRange>, gap: Int): List<Int> {
    val rows = ArrayList<Int>()
    parts.forEachIndexed { k, part ->
        if (k > 0) repeat(gap) { rows += -1 }
        for (r in part) rows += r
    }
    return rows
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

/** 쪽 높이(너비)의 이만큼 이어지는 잉크라야 칸 — 여백의 글자는 이보다 짧다 */
private const val LONG_INK_RATIO = 0.04f

/** 이보다 짧은 빈 곳은 이미 붙어 있는 것으로 보고 둔다 */
private const val MIN_GAP_RATIO = 0.002f

/** 칸 사이에 남기는 여백 — 쪽 높이의 0.1% (사용자 의견: 0.3%의 1/3이 알맞다) */
private const val KEEP_GAP_RATIO = 0.001f

/** 지울 여백이 이보다 적으면 그대로 */
private const val MIN_SAVED_RATIO = 0.005f

/** 페이지 리더(Coil)용 — 작품 보기 설정 "칸 간격 없애기" */
internal class GutterTransformation : Transformation() {
    override val cacheKey: String = "gutters-3"

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
