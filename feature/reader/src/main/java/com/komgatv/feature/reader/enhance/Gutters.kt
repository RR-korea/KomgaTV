package com.komgatv.feature.reader.enhance

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 칸(패널) 사이 위아래 여백을 없애 칸끼리 붙이고, 칸들을 쪽 가운데(세로)로 모은다. 좌우 여백은 그대로.
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
 * 여백을 가로지르는 좁은 것(칸 사이에 걸친 글 상자·말풍선)은 그 줄에 글자가 없으면 여백으로 본다 —
 * 상자는 빈 안쪽만 짧아지고, 남기는 여백 줄은 원래 여백 줄을 써서 상자 테두리가 끊기지 않는다.
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
    val keepGap = KEEP_GAP_ROWS
    val minGapX = (width * MIN_GAP_RATIO).roundToInt().coerceAtLeast(2)
    val minStackGap = (height * MIN_STACK_GAP_RATIO).roundToInt().coerceAtLeast(minGap)
    val fill = (0xFF shl 24) or (bg shl 16) or (bg shl 8) or bg

    fun content(y: Int, x0: Int, x1: Int) = rowIsContent(ink, tall, width, y, x0, x1)
    val bands = segments(height, minGap) { y -> content(y, 0, width) }
    if (bands.isEmpty()) return null
    var columnsMoved = false
    val blocks = bands.map { band ->
        val bandH = band.last - band.first + 1
        val block = pixels.copyOfRange(band.first * width, (band.last + 1) * width)
        val columns = segments(width, minGapX) { x -> band.any { y -> wide[y * width + x] } }
        // 좁은 조각으로 나뉘면 칸 묶음이 아니라 그림이 갈라진 것 — 띠를 그대로 둔다
        if (columns.size < 2 || columns.any { it.last - it.first + 1 < width * MIN_STACK_WIDTH_RATIO }) return@map block
        for (col in columns) {
            val x0 = col.first
            val span = col.last - col.first + 1
            // 묶음 안에서는 잉크가 조금이라도 있는 줄은 칸 — 위 테두리 없이 쪽 끝까지 이어진 그림·말풍선을 자르지 않게
            val looseInk = (span * STACK_INK_RATIO).roundToInt().coerceAtLeast(2)
            val parts = segments(bandH, minStackGap) { i ->
                content(band.first + i, x0, x0 + span) || inkCount(ink, width, band.first + i, x0, x0 + span) > looseInk
            }
            if (parts.isEmpty()) continue
            val rows = stack(parts, keepGap) { gap -> gap.minBy { i -> inkCount(ink, width, band.first + i, x0, x0 + span) } }
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
    blocks.forEachIndexed { k, block ->
        if (k > 0) {
            // 띠 사이 여백 줄 — 원래 여백에서 잉크가 가장 적은 줄 (걸친 글 상자 테두리만 남는다)
            val gap = bands[k - 1].last + 1 until bands[k].first
            val row = gap.minBy { inkCount(ink, width, it, 0, width) }
            repeat(keepGap) {
                System.arraycopy(pixels, row * width, out, y * width, width)
                y++
            }
        }
        System.arraycopy(block, 0, out, y * width, block.size)
        y += block.size / width
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

/**
 * 이 줄이 칸인지 — 세로로 긴 잉크가 지나가면 칸. 다만 그것이 좁은 곳([OVERLAY_SPAN_RATIO] 미만)에만 있고
 * 그 사이에 짧은 잉크(글자·그림)가 없으면 여백을 가로지르는 글 상자·말풍선 테두리로 보고 여백으로 친다.
 */
private fun rowIsContent(ink: BooleanArray, tall: BooleanArray, width: Int, y: Int, x0: Int, x1: Int): Boolean {
    val base = y * width
    var first = -1
    var last = -1
    for (x in x0 until x1) {
        if (tall[base + x]) {
            if (first < 0) first = x
            last = x
        }
    }
    if (first < 0) return false
    if (last - first + 1 >= (x1 - x0) * OVERLAY_SPAN_RATIO) return true
    var loose = 0
    for (x in first..last) if (ink[base + x] && !tall[base + x]) loose++
    return loose > OVERLAY_NOISE
}

private fun inkCount(ink: BooleanArray, width: Int, y: Int, x0: Int, x1: Int): Int {
    val base = y * width
    var n = 0
    for (x in x0 until x1) if (ink[base + x]) n++
    return n
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

/** 구간들을 [gap]줄 사이를 두고 쌓은 원래 줄 번호. 사이 줄은 원래 여백에서 [pick]이 고른 줄 */
private fun stack(parts: List<IntRange>, gap: Int, pick: (IntRange) -> Int): List<Int> {
    val rows = ArrayList<Int>()
    parts.forEachIndexed { k, part ->
        if (k > 0) {
            val row = pick(parts[k - 1].last + 1 until part.first)
            repeat(gap) { rows += row }
        }
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

/** 여백을 가로지르는 것이 이 폭(비율)보다 좁으면 글 상자·말풍선으로 본다 */
private const val OVERLAY_SPAN_RATIO = 0.3f

/** 글 상자 안 줄에 이만큼까지의 짧은 잉크는 잡티로 본다 */
private const val OVERLAY_NOISE = 2

/** 칸 묶음 안에서 여백으로 볼 가장 짧은 빈 곳 — 그림 속 흰 줄은 이보다 짧다 */
private const val MIN_STACK_GAP_RATIO = 0.006f

/** 칸 묶음은 쪽 너비의 이만큼은 돼야 한다 */
private const val MIN_STACK_WIDTH_RATIO = 0.1f

/** 칸 묶음 안 줄에 잉크가 이 비율을 넘으면 칸 */
private const val STACK_INK_RATIO = 0.01f

/** 이보다 짧은 빈 곳은 이미 붙어 있는 것으로 보고 둔다 */
private const val MIN_GAP_RATIO = 0.002f

/** 칸 사이에 남기는 여백 줄 수 — 0: 칸 테두리끼리 붙인다 (사용자 의견) */
private const val KEEP_GAP_ROWS = 0

/** 지울 여백이 이보다 적으면 그대로 */
private const val MIN_SAVED_RATIO = 0.005f

/** 페이지 리더(Coil)용 — 작품 보기 설정 "칸 간격 없애기" */
internal class GutterTransformation : Transformation() {
    override val cacheKey: String = "gutters-5"

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
