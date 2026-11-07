package com.komgatv.feature.reader.enhance

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 칸(패널) 사이 위아래 여백을 없애 칸끼리 붙이고, 칸들을 쪽 가운데(세로)로 모은다. 좌우 여백은 그대로.
 * 쪽 크기는 바꾸지 않는다 — 비는 곳은 원래 여백의 색(흰색·검정·망점의 대표 색)으로 채운다.
 *
 * 1. 쪽 전체 폭을 가로지르는 여백으로 쪽을 가로 띠로 나누고, 띠끼리 붙여 쪽 가운데로 모은다.
 * 2. 띠 안이 세로 칸 묶음 여럿이면(옆에 세로로 긴 칸이 있는 작은 칸들) 묶음마다 위아래 여백을 붙이고
 *    쪽 가운데 쪽으로 민다 — 위쪽 띠는 아래로, 아래쪽 띠는 위로. 비는 곳은 쪽 바깥쪽에 생긴다.
 *
 * 여백 줄은 둘 중 하나:
 * - 바탕과 다른 잉크가 세로로 길게(칸 옆 테두리·그림) 지나가지 않는 줄 — 여백의 글자(쪽 번호·역자 주)는
 *   획이 짧아 칸으로 치지 않는다. 걸친 좁은 글 상자·말풍선도 그 안이 비어 있으면 여백.
 * - 한 가지 색으로 고른 줄 (가로로 살짝 흐리게 본 뒤) — 검은 틈, 망점 그라데이션 틈. 다만 쪽 높이의
 *   [MIN_FLAT_GAP_RATIO]보다 얇으면 칸 테두리로, 칸 사이에서 [MAX_FLAT_GAP_RATIO]보다 길면 그림 속 검은 곳으로 보고 둔다.
 * 쪽 왼쪽·오른쪽 끝(스캔 테두리)은 보지 않는다.
 * 여백은 대부분([CLEAN_GAP_RATIO] 이상) 빈 줄이어야 한다 — 글자·그림이 빽빽한 곳(한 장 그림, 글 쪽)은 여백이 아니다.
 *
 * @return 바꾼 픽셀, 바꿀 것이 거의 없으면 null
 */
internal fun collapseGutters(pixels: IntArray, width: Int, height: Int): IntArray? {
    if (width < 8 || height < 32) return null
    val page = PageMaps(pixels, width, height)
    val edgeX = (width * EDGE_RATIO).roundToInt()
    val minGap = (height * MIN_GAP_RATIO).roundToInt().coerceAtLeast(2)
    val minGapX = (width * MIN_GAP_RATIO).roundToInt().coerceAtLeast(2)
    val minStackGap = (height * MIN_STACK_GAP_RATIO).roundToInt().coerceAtLeast(minGap)
    val minFlatGap = (height * MIN_FLAT_GAP_RATIO).roundToInt().coerceAtLeast(3)
    val maxFlatGap = (height * MAX_FLAT_GAP_RATIO).roundToInt()

    val bands = segments(
        height, minGap, minFlatGap, maxFlatGap,
        isContent = { y -> page.inkContent(y, 0, width) && !page.flatGutter(y, edgeX, width - edgeX) },
        flatOnly = { y -> page.inkContent(y, 0, width) },
        keepGap = { gap -> !page.cleanGap(gap, edgeX, width - edgeX) },
    )
    if (bands.isEmpty()) return null
    var columnsMoved = false
    val blocks = bands.map { band ->
        val bandH = band.last - band.first + 1
        val block = pixels.copyOfRange(band.first * width, (band.last + 1) * width)
        fun wideInk(x: Int) = band.any { y -> page.wide[y * width + x] }
        val columns = segments(
            width, minGapX,
            minFlat = (width * MIN_FLAT_GAP_RATIO).roundToInt().coerceAtLeast(3),
            maxFlat = Int.MAX_VALUE,
            isContent = { x -> wideInk(x) && !page.flatColumn(x, band.first, band.last + 1) },
            flatOnly = { x -> wideInk(x) },
        )
        // 좁은 조각으로 나뉘면 칸 묶음이 아니라 그림이 갈라진 것 — 띠를 그대로 둔다
        if (columns.size < 2 || columns.any { it.last - it.first + 1 < width * MIN_STACK_WIDTH_RATIO }) return@map block
        for (col in columns) {
            val x0 = col.first
            val x1 = col.last + 1
            // 묶음 안에서는 잉크가 조금이라도 있는 줄은 칸 — 위 테두리 없이 쪽 끝까지 이어진 그림·말풍선을 자르지 않게
            val looseInk = ((x1 - x0) * STACK_INK_RATIO).roundToInt().coerceAtLeast(2)
            fun inked(i: Int) = page.inkContent(band.first + i, x0, x1) || page.inkCount(band.first + i, x0, x1) > looseInk
            val parts = segments(
                bandH, minStackGap, minFlatGap, maxFlatGap,
                isContent = { i -> inked(i) && !page.flatGutter(band.first + i, x0, x1) },
                flatOnly = { i -> inked(i) },
            )
            if (parts.isEmpty()) continue
            val rows = parts.flatMap { it.toList() }
            if (rows.size >= bandH) continue
            // 쪽 가운데 쪽으로 — 위쪽 띠는 아래로, 아래쪽 띠는 위로, 가운데에 걸치면 가운데
            val top = when {
                band.last < height / 2 -> bandH - rows.size
                band.first > height / 2 -> 0
                else -> (bandH - rows.size) / 2
            }
            // 비는 곳은 이 묶음의 원래 여백 중 가장 고른 줄의 대표 색으로 채운다 (검은 틈이면 검정)
            val kept = rows.toHashSet()
            val pad = (0 until bandH).filter { it !in kept }.minBy { page.outliers(band.first + it, x0, x1) }
            val padColor = page.typicalColor(pixels, band.first + pad, x0, x1)
            for (i in 0 until bandH) block.fill(padColor, i * width + x0, i * width + x1)
            rows.forEachIndexed { i, r ->
                System.arraycopy(pixels, (band.first + r) * width + x0, block, (top + i) * width + x0, x1 - x0)
            }
            columnsMoved = true
        }
        block
    }

    val total = blocks.sumOf { it.size / width }
    val removed = (0 until height).filter { y -> bands.none { y in it } }
    if (removed.isEmpty() || (!columnsMoved && removed.size < height * MIN_SAVED_RATIO)) return null
    // 위아래 빈 곳은 쪽 위·아래 절반의 원래 여백 중 가장 고른 줄의 대표 색 (위는 검정 틈, 아래는 흰 종이인 쪽도 있다)
    fun padColor(rows: List<Int>) = page.typicalColor(pixels, rows.ifEmpty { removed }.minBy { page.outliers(it, 0, width) }, 0, width)
    val start = (height - total) / 2
    val out = IntArray(pixels.size)
    out.fill(padColor(removed.filter { it < height / 2 }), 0, start * width)
    var y = start
    blocks.forEach { block ->
        System.arraycopy(block, 0, out, y * width, block.size)
        y += block.size / width
    }
    out.fill(padColor(removed.filter { it >= height / 2 }), y * width, out.size)
    return out
}

/** 쪽을 보는 표들 — 잉크(바탕과 다른 곳), 세로·가로로 길게 이어진 잉크, 살짝 흐리게 한 밝기 */
private class PageMaps(pixels: IntArray, private val width: Int, height: Int) {
    private val bg: Int
    private val ink: BooleanArray
    private val tall: BooleanArray
    /** 세로로 긴 잉크가 아닌 잉크 중 위나 아래도 잉크인 곳 — 글자 획·그림. 망점 하나하나는 빠진다 */
    private val stroke: BooleanArray
    val wide: BooleanArray
    /** 가로로 흐리게 (줄이 고른지 볼 때), 세로로 흐리게 (열이 고른지 볼 때) — 망점이 고른 회색이 된다 */
    private val blurH: IntArray
    private val blurV: IntArray

    init {
        val lum = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        bg = backgroundLuminance(lum)
        ink = BooleanArray(pixels.size) { abs(lum[it] - bg) > INK_DIFF }
        val edgeX = (width * EDGE_RATIO).roundToInt()
        val minRunV = (height * LONG_INK_RATIO).roundToInt().coerceAtLeast(8)
        val minRunH = (width * LONG_INK_RATIO).roundToInt().coerceAtLeast(8)
        tall = longRuns(ink, width, height, vertical = true, minRun = minRunV, skipEdge = edgeX)
        wide = longRuns(ink, width, height, vertical = false, minRun = minRunH, skipEdge = 0)
        val loose = BooleanArray(pixels.size) { ink[it] && !tall[it] }
        stroke = BooleanArray(pixels.size) { i ->
            loose[i] && ((i >= width && loose[i - width]) || (i + width < loose.size && loose[i + width]))
        }
        blurH = boxBlur(lum, width, height, BLUR_RADIUS, horizontal = true)
        blurV = boxBlur(lum, width, height, BLUR_RADIUS, horizontal = false)
    }

    /**
     * 세로로 긴 잉크가 지나가면 칸. 다만 그것이 좁은 곳([OVERLAY_SPAN_RATIO] 미만)에만 있고
     * 그 사이에 짧은 잉크(글자·그림)가 없으면 여백을 가로지르는 글 상자·말풍선 테두리로 보고 여백으로 친다.
     */
    fun inkContent(y: Int, x0: Int, x1: Int): Boolean {
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

    fun inkCount(y: Int, x0: Int, x1: Int): Int {
        val base = y * width
        var n = 0
        for (x in x0 until x1) if (ink[base + x]) n++
        return n
    }

    /** 줄에서 가운데 밝기(중앙값)와 [FLAT_DIFF] 넘게 다른 칸 수 — 적을수록 고른 줄 */
    fun outliers(y: Int, x0: Int, x1: Int): Int = outliers(x1 - x0) { blurH[y * width + x0 + it] }

    /** 줄의 대표 색 — 흐리게 본 밝기가 가운데 값 가까운 칸들의 평균 색 (망점이면 그 회색, 색 틈이면 그 색) */
    fun typicalColor(pixels: IntArray, y: Int, x0: Int, x1: Int): Int {
        val base = y * width
        val sorted = (x0 until x1).sortedBy { blurH[base + it] }
        val mid = blurH[base + sorted[sorted.size / 2]]
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (x in x0 until x1) {
            if (abs(blurH[base + x] - mid) > FLAT_DIFF) continue
            val c = pixels[base + x]
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
            n++
        }
        if (n == 0) return (0xFF shl 24) or (mid shl 16) or (mid shl 8) or mid
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    fun flat(y: Int, x0: Int, x1: Int): Boolean = x1 > x0 && outliers(y, x0, x1) <= (x1 - x0) * FLAT_OUTLIER_RATIO

    /** 바탕과 다른 한 가지 색으로 고른 줄 — 검은 틈·망점 틈. 바탕색으로 고른 줄(액자 선만 지나가는 흰 줄)은 아니다 */
    fun flatGutter(y: Int, x0: Int, x1: Int): Boolean =
        flat(y, x0, x1) && abs(median(x1 - x0) { blurH[y * width + x0 + it] } - bg) > FLAT_DIFF

    /** 여백 후보가 정말 비었는지 — 빈 줄(고른 색 줄 포함)이 [CLEAN_GAP_RATIO] 이상이고 획이 평균 [GAP_STROKE_RATIO] 이하 */
    fun cleanGap(rows: IntRange, x0: Int, x1: Int): Boolean {
        val noise = ((x1 - x0) * CLEAN_ROW_RATIO).roundToInt().coerceAtLeast(OVERLAY_NOISE)
        var clean = 0
        var strokes = 0L
        for (y in rows) {
            if (flatGutter(y, x0, x1)) {
                clean++
                continue
            }
            var n = 0
            for (x in x0 until x1) if (stroke[y * width + x]) n++
            if (n <= noise) clean++
            strokes += n
        }
        val count = rows.last - rows.first + 1
        return clean >= count * CLEAN_GAP_RATIO && strokes <= count.toLong() * (x1 - x0) * GAP_STROKE_RATIO
    }

    fun flatColumn(x: Int, y0: Int, y1: Int): Boolean =
        outliers(y1 - y0) { blurV[(y0 + it) * width + x] } <= (y1 - y0) * FLAT_OUTLIER_RATIO

    private inline fun outliers(n: Int, value: (Int) -> Int): Int {
        val hist = IntArray(256)
        for (i in 0 until n) hist[value(i)]++
        val median = median(hist, n)
        var out = 0
        for (v in 0..255) if (abs(v - median) > FLAT_DIFF) out += hist[v]
        return out
    }

    private inline fun median(n: Int, value: (Int) -> Int): Int {
        val hist = IntArray(256)
        for (i in 0 until n) hist[value(i)]++
        return median(hist, n)
    }

    private fun median(hist: IntArray, n: Int): Int {
        var median = 0
        var seen = 0
        while (median < 255) {
            seen += hist[median]
            if (seen * 2 >= n) break
            median++
        }
        return median
    }
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

/** 한 방향으로 2r+1칸 평균 — 이웃 줄(열)의 칸 테두리가 섞이지 않게 한 방향으로만. 가장자리는 있는 칸만 평균 */
private fun boxBlur(lum: IntArray, width: Int, height: Int, r: Int, horizontal: Boolean): IntArray {
    val out = IntArray(lum.size)
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val prefix = IntArray(length + 1)
    for (line in 0 until lines) {
        fun index(i: Int) = if (horizontal) line * width + i else i * width + line
        for (i in 0 until length) prefix[i + 1] = prefix[i] + lum[index(i)]
        for (i in 0 until length) {
            val a = maxOf(0, i - r)
            val b = minOf(length, i + r + 1)
            out[index(i)] = (prefix[b] - prefix[a]) / (b - a)
        }
    }
    return out
}

/**
 * 내용이 있는 구간들 — [minGap]보다 짧은 빈 곳은 구간 안으로 치고, 앞뒤 빈 곳은 뺀다.
 * 고른 색이라서만 빈 곳([flatOnly])이 이어진 구간은 [minFlat]보다 짧으면(칸 테두리 굵기) 내용,
 * 칸 사이에서 [maxFlat]보다 길면(그림 속 검은 곳) 내용으로 친다. [keepGap]이 참인 빈 곳(빽빽한 글·그림)도 내용.
 */
private fun segments(
    length: Int,
    minGap: Int,
    minFlat: Int,
    maxFlat: Int,
    isContent: (Int) -> Boolean,
    flatOnly: (Int) -> Boolean = { false },
    keepGap: (IntRange) -> Boolean = { false },
): List<IntRange> {
    val content = BooleanArray(length) { isContent(it) }
    val first = content.indexOfFirst { it }
    val last = content.indexOfLast { it }
    var i = 0
    while (i < length) {
        if (content[i] || !flatOnly(i)) {
            i++
            continue
        }
        val start = i
        while (i < length && !content[i] && flatOnly(i)) i++
        val inner = start > first && i - 1 < last
        if (i - start < minFlat || (inner && i - start > maxFlat)) for (k in start until i) content[k] = true
    }
    i = 0
    while (i < length) {
        if (content[i]) {
            i++
            continue
        }
        val start = i
        while (i < length && !content[i]) i++
        if (keepGap(start until i)) for (k in start until i) content[k] = true
    }
    val out = ArrayList<IntRange>()
    var start = -1
    var lastContent = -1
    for (k in 0 until length) {
        if (!content[k]) continue
        if (start < 0) {
            start = k
        } else if (k - lastContent - 1 >= minGap) {
            out += start..lastContent
            start = k
        }
        lastContent = k
    }
    if (start >= 0) out += start..lastContent
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

/** 쪽 높이(너비)의 이만큼 이어지는 잉크라야 칸 — 여백의 글자는 이보다 짧다 */
private const val LONG_INK_RATIO = 0.04f

/** 여백을 가로지르는 것이 이 폭(비율)보다 좁으면 글 상자·말풍선으로 본다 */
private const val OVERLAY_SPAN_RATIO = 0.3f

/** 글 상자 안 줄에 이만큼까지의 짧은 잉크는 잡티로 본다 */
private const val OVERLAY_NOISE = 2

/** 고른 색 줄을 볼 때 흐리게 하는 반경 (5칸) — 망점이 고른 회색이 된다 */
private const val BLUR_RADIUS = 2

/** 가운데 밝기와 이만큼 넘게 다르면 튀는 칸 */
private const val FLAT_DIFF = 30

/** 튀는 칸이 이 비율 이하면 고른 줄 (검은 틈·망점 틈) */
private const val FLAT_OUTLIER_RATIO = 0.03f

/** 고른 색 빈 곳이 쪽 높이의 이만큼보다 얇으면 틈이 아니라 칸 테두리 */
private const val MIN_FLAT_GAP_RATIO = 0.008f

/** 고른 색 빈 곳이 쪽 높이의 이만큼보다 길면 틈이 아니라 그림 속 고른 곳 */
private const val MAX_FLAT_GAP_RATIO = 0.06f

/** 여백 후보의 줄 중 이만큼은 빈 줄이어야 여백 — 글 쪽·한 장 그림은 빈 줄이 드물다 */
private const val CLEAN_GAP_RATIO = 0.6f

/** 획이 이 비율 이하인 줄은 빈 줄 (잡티·망점 틈) */
private const val CLEAN_ROW_RATIO = 0.01f

/** 여백 후보 전체에서 획이 평균 이 비율을 넘으면 여백이 아니다 */
private const val GAP_STROKE_RATIO = 0.1f

/** 칸 묶음 안에서 여백으로 볼 가장 짧은 빈 곳 — 그림 속 흰 줄은 이보다 짧다 */
private const val MIN_STACK_GAP_RATIO = 0.006f

/** 칸 묶음은 쪽 너비의 이만큼은 돼야 한다 */
private const val MIN_STACK_WIDTH_RATIO = 0.1f

/** 칸 묶음 안 줄에 잉크가 이 비율을 넘으면 칸 */
private const val STACK_INK_RATIO = 0.01f

/** 이보다 짧은 빈 곳은 이미 붙어 있는 것으로 보고 둔다 */
private const val MIN_GAP_RATIO = 0.002f

/** 지울 여백이 이보다 적으면 그대로 */
private const val MIN_SAVED_RATIO = 0.005f

/** 페이지 리더(Coil)용 — 작품 보기 설정 "칸 간격 없애기" */
internal class GutterTransformation : Transformation() {
    override val cacheKey: String = "gutters-9"

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
