package com.komgatv.feature.reader.enhance

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.sync.withLock

/**
 * 칸(패널) 사이 위아래 여백을 없애 칸끼리 붙이고, 칸들을 쪽 가운데(세로)로 모은다. 좌우 여백은 그대로.
 * 쪽 크기는 바꾸지 않는다 — 비는 곳은 원래 여백의 색(흰색·검정·망점의 대표 색)으로 채운다.
 *
 * 1. 쪽 전체 폭을 가로지르는 여백으로 쪽을 가로 띠로 나누고, 띠끼리 붙여 쪽 가운데로 모은다.
 * 2. 띠 안이 세로 칸 묶음 여럿이면(옆에 세로로 긴 칸이 있는 작은 칸들) 묶음마다 위아래 여백을 붙이고
 *    쪽 가운데 쪽으로 민다 — 위쪽 띠는 아래로, 아래쪽 띠는 위로. 비는 곳은 쪽 바깥쪽에 생긴다.
 * 3. 그래도 붙일 줄이 없는 띠·묶음은 비스듬하거나 계단처럼 엇갈린 여백을 찾는다 ([findRibbons]) —
 *    열마다 빼는 자리는 달라도 빼는 줄 수는 같아서 아래 칸이 일그러지지 않고 통째로 붙는다.
 *
 * 여백 줄은 둘 중 하나:
 * - 바탕과 다른 잉크가 세로로 길게(칸 옆 테두리·그림) 지나가지 않는 줄 — 여백의 글자(쪽 번호·역자 주)는
 *   획이 짧아 칸으로 치지 않는다. 걸친 좁은 글 상자·말풍선도 그 안이 비어 있으면 여백.
 * - 한 가지 색으로 고른 줄 (가로로 살짝 흐리게 본 뒤) — 검은 틈, 망점 그라데이션 틈. 다만 쪽 높이의
 *   [MIN_FLAT_GAP_RATIO]보다 얇으면 칸 테두리로, 칸 사이에서 [MAX_FLAT_GAP_RATIO]보다 길면 그림 속 검은 곳으로 보고 둔다.
 * 쪽 왼쪽·오른쪽 끝에서 쪽 높이 대부분을 지나는 줄(스캔 테두리)은 보지 않는다.
 * 여백은 대부분([CLEAN_GAP_RATIO] 이상) 빈 줄이어야 한다 — 글자·그림이 빽빽한 곳(한 장 그림, 글 쪽)은 여백이 아니다.
 * 다만 위아래가 칸 테두리로 막힌 여백에 좁게 걸친 효과음·말풍선은 여백과 함께 짧아진다.
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

    /** 띠([band]) 쪽 가운데 쪽으로 붙일 때 내용의 첫 줄 — 위쪽 띠는 아래로, 아래쪽 띠는 위로, 가운데에 걸치면 가운데 */
    fun towardCentre(band: IntRange, removed: Int): Int = when {
        band.last < height / 2 -> removed
        band.first > height / 2 -> 0
        else -> removed / 2
    }

    /**
     * [band]의 [x0, x1) 열에서 비스듬한·엇갈린 여백 띠를 빼고 쪽 가운데 쪽으로 붙인다 — [block]에 쓴다.
     * 열마다 같은 줄 수를 빼므로 아래 칸은 일그러지지 않는다. 비는 곳은 뺀 여백의 평균 색.
     */
    fun collapseRibbons(block: IntArray, band: IntRange, x0: Int, x1: Int): Boolean {
        val ribbons = findRibbons(page, x0, x1, band.first, band.last + 1, minStackGap, maxFlatGap, width, edgeX)
        if (ribbons.isEmpty()) return false
        val bandH = band.last - band.first + 1
        val total = ribbons.sumOf { it.rows }
        val top = towardCentre(band, total)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (x in x0 until x1) for (ribbon in ribbons) {
            for (y in ribbon.start[x - x0] until ribbon.start[x - x0] + ribbon.rows) {
                val c = pixels[y * width + x]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
            }
        }
        val pad = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        for (x in x0 until x1) {
            var out = top
            var k = 0
            var y = band.first
            for (i in 0 until bandH) block[i * width + x] = pad
            while (y <= band.last) {
                if (k < ribbons.size && y == ribbons[k].start[x - x0]) {
                    y += ribbons[k].rows
                    k++
                    continue
                }
                block[out++ * width + x] = pixels[y * width + x]
                y++
            }
        }
        return true
    }

    val bands = segments(
        height, minGap, minFlatGap, maxFlatGap,
        isContent = { y -> page.bandInk(y, edgeX) && !page.flatGutter(y, edgeX, width - edgeX) },
        flatOnly = { y -> page.bandInk(y, edgeX) },
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
        if (columns.isEmpty() || columns.any { it.last - it.first + 1 < width * MIN_STACK_WIDTH_RATIO }) return@map block
        if (columns.size == 1) {
            if (collapseRibbons(block, band, columns[0].first, columns[0].last + 1)) columnsMoved = true
            return@map block
        }
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
            if (rows.size >= bandH) {
                if (collapseRibbons(block, band, x0, x1)) columnsMoved = true
                continue
            }
            val top = towardCentre(band, bandH - rows.size)
            // 비는 곳은 이 묶음의 원래 여백 중 가장 고른 줄의 대표 색으로 채운다 (검은 틈이면 검정)
            val kept = rows.toHashSet()
            val pad = page.evenest((0 until bandH).filter { it !in kept }.map { band.first + it }, x0, x1) - band.first
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
    if (!columnsMoved && removed.size < height * MIN_SAVED_RATIO) return null
    // 위아래 빈 곳은 쪽 위·아래 절반의 원래 여백 중 가장 고른 줄의 대표 색 (위는 검정 틈, 아래는 흰 종이인 쪽도 있다)
    fun padColor(rows: List<Int>) = page.typicalColor(pixels, page.evenest(rows.ifEmpty { removed }, 0, width), 0, width)
    val start = (height - total) / 2
    val out = IntArray(pixels.size)
    if (removed.isEmpty()) {
        // 쪽 전체 여백은 없고 칸 묶음만 움직였다
        blocks.forEachIndexed { i, block -> System.arraycopy(block, 0, out, bands[i].first * width, block.size) }
        return out
    }
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
private class PageMaps(pixels: IntArray, private val width: Int, private val height: Int) {
    private val bg: Int
    private val ink: BooleanArray
    private val tall: BooleanArray
    /** 세로로 긴 잉크가 아닌 잉크 중 위나 아래도 잉크인 곳 — 글자 획·그림. 망점 하나하나는 빠진다 */
    private val stroke: BooleanArray
    val wide: BooleanArray
    /** 가로로 흐리게 (줄이 고른지 볼 때), 세로로 흐리게 (열이 고른지 볼 때) — 망점이 고른 회색이 된다 */
    private val blurH: IntArray
    private val blurV: IntArray
    private val lum: IntArray

    init {
        lum = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        bg = backgroundLuminance(lum)
        ink = BooleanArray(pixels.size) { abs(lum[it] - bg) > INK_DIFF }
        val edgeX = (width * EDGE_RATIO).roundToInt()
        val minRunV = (height * LONG_INK_RATIO).roundToInt().coerceAtLeast(8)
        val minRunH = (width * LONG_INK_RATIO).roundToInt().coerceAtLeast(8)
        tall = longRuns(ink, width, height, vertical = true, minRun = minRunV, edge = edgeX, maxEdgeRun = (height * SCAN_EDGE_RUN_RATIO).roundToInt())
        wide = longRuns(ink, width, height, vertical = false, minRun = minRunH)
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

    /** 칸 테두리 같은 가로선이 줄의 [BORDER_ROW_RATIO] 이상을 지나간다 */
    private fun borderRow(y: Int, x0: Int, x1: Int): Boolean {
        var n = 0
        for (x in x0 until x1) if (wide[y * width + x]) n++
        return n >= (x1 - x0) * BORDER_ROW_RATIO
    }

    /** 쪽 전체 띠를 나눌 때의 잉크 줄 — 세로로 긴 잉크가 지나가거나, 칸 테두리 같은 가로선이 길게 지나간다 (기울어진 스캔의 테두리 끝자락) */
    fun bandInk(y: Int, edgeX: Int): Boolean {
        return inkContent(y, 0, width) || borderRow(y, edgeX, width - edgeX)
    }

    fun lumAt(x: Int, y: Int): Int = lum[y.coerceIn(0, height - 1) * width + x]

    /** [x] 열의 [y0, y1) 줄들의 가장 밝고 어두운 곳 차이 — 작으면 위아래로 밋밋하다 (그림자, 빈 종이) */
    fun rowRange(x: Int, y0: Int, y1: Int): Int {
        var min = 255
        var max = 0
        for (y in y0.coerceAtLeast(0) until y1.coerceAtMost(height)) {
            val v = lum[y * width + x]
            if (v < min) min = v
            if (v > max) max = v
        }
        return max - min
    }

    /**
     * [x] 열의 [y0, y1) 안 빈 구간들, 위에서부터 — 그 열의 밝은 곳([COLUMN_PAPER_SHARE])에 가까우면 빈 곳.
     * 쪽 전체 종이색이 아니라 열마다 보므로 가운데 접힌 곳 그림자 속 여백도 빈 곳이다.
     */
    fun cleanRuns(x: Int, y0: Int, y1: Int): List<IntRange> {
        val hist = IntArray(256)
        for (y in y0 until y1) hist[lum[y * width + x]]++
        var paper = 255
        var seen = 0
        while (paper > 0 && seen + hist[paper] <= (y1 - y0) * (1 - COLUMN_PAPER_SHARE)) seen += hist[paper--]
        // 위아래로 다 어두운 열(칸 세로 테두리, 검은 칸)의 종이색은 쪽 바탕색에서 [MAX_SHADE_DIFF]까지만 어둡게 본다
        val min = maxOf(paper, bg - MAX_SHADE_DIFF) - INK_DIFF
        val runs = ArrayList<IntRange>()
        var start = -1
        for (y in y0..y1) {
            if (y < y1 && lum[y * width + x] >= min) {
                if (start < 0) start = y
            } else if (start >= 0) {
                runs += start until y
                start = -1
            }
        }
        return runs
    }

    fun inkCount(y: Int, x0: Int, x1: Int): Int {
        val base = y * width
        var n = 0
        for (x in x0 until x1) if (ink[base + x]) n++
        return n
    }

    /** 줄에서 가운데 밝기(중앙값)와 [FLAT_DIFF] 넘게 다른 칸 수 — 적을수록 고른 줄 */
    fun outliers(y: Int, x0: Int, x1: Int): Int = outliers(x1 - x0) { blurH[y * width + x0 + it] }

    /** 가장 고른 줄 — 똑같이 고르면 바탕색에 가까운 줄 (스캔 끝의 회색 줄보다 종이) */
    fun evenest(rows: List<Int>, x0: Int, x1: Int): Int =
        rows.minWith(compareBy({ outliers(it, x0, x1) }, { abs(median(x1 - x0) { i -> blurH[it * width + x0 + i] } - bg) }))

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
        val columns = IntArray(x1 - x0)
        for (y in rows) {
            if (flatGutter(y, x0, x1)) {
                clean++
                continue
            }
            var n = 0
            for (x in x0 until x1) if (stroke[y * width + x]) {
                n++
                columns[x - x0]++
            }
            if (n <= noise) clean++
            strokes += n
        }
        val count = rows.last - rows.first + 1
        if (clean >= count * CLEAN_GAP_RATIO && strokes <= count.toLong() * (x1 - x0) * GAP_STROKE_RATIO) return true
        // 위아래가 칸 테두리로 막혔고 획이 좁은 곳에만 몰려 있으면 여백에 걸친 효과음·말풍선 — 그만큼 짧아져도 여백으로 친다
        return count <= height * MAX_FLAT_GAP_RATIO &&
            rows.first > 0 && borderRow(rows.first - 1, x0, x1) && rows.last < height - 1 && borderRow(rows.last + 1, x0, x1) &&
            narrowestSpan(columns, strokes * OVERLAY_STROKE_SHARE) < (x1 - x0) * OVERLAY_SPAN_RATIO
    }

    /** [counts]의 합이 [share] 이상인 가장 좁은 연속 구간의 폭 */
    private fun narrowestSpan(counts: IntArray, share: Double): Int {
        var best = counts.size
        var sum = 0L
        var a = 0
        for (b in counts.indices) {
            sum += counts[b]
            while (a <= b && sum - counts[a] >= share) sum -= counts[a++]
            if (sum >= share) best = minOf(best, b - a + 1)
        }
        return best
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

/**
 * 세로(또는 가로)로 [minRun] 이상 이어지는 잉크만 남긴 표.
 * 세로일 때 왼쪽·오른쪽 끝 [edge]열 안에서는 [maxEdgeRun]보다 긴 것(쪽 높이 대부분을 지나는 스캔 테두리)은 뺀다 —
 * 쪽 끝에 바짝 붙은 칸 테두리는 칸 높이만큼만 이어지므로 남는다.
 */
private fun longRuns(
    ink: BooleanArray,
    width: Int,
    height: Int,
    vertical: Boolean,
    minRun: Int,
    edge: Int = 0,
    maxEdgeRun: Int = Int.MAX_VALUE,
): BooleanArray {
    val out = BooleanArray(ink.size)
    val lines = if (vertical) 0 until width else 0 until height
    val length = if (vertical) height else width
    for (line in lines) {
        fun index(i: Int) = if (vertical) i * width + line else line * width + i
        val maxRun = if (vertical && (line < edge || line >= width - edge)) maxEdgeRun else Int.MAX_VALUE
        var start = -1
        for (i in 0..length) {
            if (i < length && ink[index(i)]) {
                if (start < 0) start = i
            } else if (start >= 0) {
                if (i - start in minRun..maxRun) for (k in start until i) out[index(k)] = true
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

/** 비스듬한·엇갈린 여백 띠 — [x0]부터 열마다 뺄 줄의 시작 [start] (쪽 기준)과 뺄 줄 수 [rows] */
private class Ribbon(val x0: Int, val start: IntArray, val rows: Int)

/**
 * [x0, x1) 열을 왼쪽 끝부터 오른쪽 끝까지 가로지르는 빈 띠들 — 줄이 아니라 열마다 빈 구간을 하나씩 골라 잇는다.
 * 칸 사이처럼 [maxGap] 이하로 짧고 위아래가 잉크로 막힌 구간이 대부분([RIBBON_SHORT_RATIO])이며 그 위아래 끝이
 * 매끈해야(칸 테두리) 한다 — 긴 구간(아래 칸 흰 바탕과 이어진 곳, 엇갈린 칸 사이 세로 여백)은 잇는 데만 쓴다.
 * 모든 열에서 같은 줄 수를 빼되, 열마다 앞·이·다음 열이 함께 빈 곳에서 빼므로 빼고 나서 이웃 열끼리 어긋나는 곳은 빈 곳뿐이다.
 * 양 끝 [RIBBON_EDGE_RATIO] 안에서는 길이 늦게 시작하거나 일찍 끝나도 된다 — 거기(가운데 접힌 곳 그림자, 쪽 여백)서는
 * 길 끝 자리에서 같은 줄 수를 빼되, 그 자리가 위아래로 밋밋해야 한다.
 */
private fun findRibbons(
    page: PageMaps,
    x0: Int,
    x1: Int,
    y0: Int,
    y1: Int,
    minGap: Int,
    maxGap: Int,
    pageWidth: Int,
    scanEdge: Int,
): List<Ribbon> {
    val n = x1 - x0
    if (n < 3) return emptyList()
    fun short(r: IntRange) = r.last - r.first + 1 in minGap..maxGap && r.first > y0 && r.last < y1 - 1
    val edge = (n * RIBBON_EDGE_RATIO).roundToInt()
    val runs = Array(n) { i -> page.cleanRuns(x0 + i, y0, y1).filter { it.last - it.first + 1 >= minGap } }
    fun overlap(a: IntRange, b: IntRange) = minOf(a.last, b.last) - maxOf(a.first, b.first) + 1
    // 긴 구간을 지나는 값 — 양 끝 [edge] 안(쪽 여백)에서는 따지지 않는다. 거기선 덜 오르내리는 길이 낫다 (끝의 잡티 사이로 가지 않게)
    fun step(i: Int, run: IntRange) = if (short(run) || i < edge || i >= n - edge) 0L else RIBBON_LONG_COST
    fun shift(a: IntRange, b: IntRange) = (abs(a.first - b.first) + abs(a.last - b.last)).toLong()

    /**
     * 길 위의 이웃한 두 열 구간 — 앞 열 구간 [p], 이 열 구간 [q], 여기까지의 값, 앞 열 상태 [back].
     * [fresh]면 이 열에서 시작한 길 ([p]도 이 열 구간).
     */
    class Step(val p: Int, val q: Int, val cost: Long, val back: Int, val fresh: Boolean)
    // 열마다 구간까지 오는 가장 싼 길 — 긴 구간을 덜 지나고, 덜 오르내리는 길. 앞·이·다음 열 구간이 [minGap] 이상 함께 비어야 잇는다.
    // 왼쪽 끝 열의 모든 빈 구간(아래 칸 흰 바탕과 이어져 긴 구간이 된 여백도)과, 왼쪽 끝 가까이의 짧은 구간에서 떠난다
    val steps = arrayOfNulls<List<Step>>(n)
    for (i in 0 until n) {
        val best = HashMap<Long, Step>()
        fun offer(key: Long, s: Step) {
            if ((best[key]?.cost ?: Long.MAX_VALUE) > s.cost) best[key] = s
        }
        for ((q, run) in runs[i].withIndex()) {
            if (i == 0 || (i <= edge && short(run))) offer(-1L - q, Step(q, q, i * RIBBON_EDGE_COST + step(i, run), -1, fresh = true))
        }
        if (i > 0) {
            for ((s, before) in steps[i - 1]!!.withIndex()) {
                val b = runs[i - 1][before.q]
                val a = if (before.fresh) b else runs[i - 2][before.p]
                for ((r, c) in runs[i].withIndex()) {
                    val together = minOf(a.last, b.last, c.last) - maxOf(a.first, b.first, c.first) + 1
                    if (together < minGap) continue
                    offer(before.q.toLong() shl 32 or r.toLong(), Step(before.q, r, before.cost + step(i, c) + shift(b, c), s, fresh = false))
                }
            }
        }
        steps[i] = best.values.toList()
    }
    val found = ArrayList<Pair<Long, Ribbon>>()
    // 오른쪽 끝 가까이의 짧은 구간마다 거기까지 온 가장 싼 길 하나
    for (end in n - 1 downTo maxOf(1, n - 1 - edge)) {
        val ends = steps[end]!!.filter { !it.fresh && short(runs[end][it.q]) }.groupBy { it.q }.values.map { it.minBy { s -> s.cost } }
        for (last in ends) {
            val chosen = ArrayList<IntRange>()
            var at: Step = last
            var col = end
            chosen += runs[col][at.q]
            while (!at.fresh) {
                col--
                at = steps[col]!![at.back]
                chosen += runs[col][at.q]
            }
            chosen.reverse()
            // 길은 짧은 구간이 매끈하게 [RIBBON_ANCHOR]열 넘게 이어지는 첫 곳부터 마지막 곳까지 —
            // 그 바깥(쪽 여백, 쪽 끝 잡티 사이)은 길로 치지 않고 아래처럼 길 끝 자리를 그대로 잇는다
            fun anchored(m: Int) = m + RIBBON_ANCHOR <= chosen.size && (m until m + RIBBON_ANCHOR).all { k ->
                short(chosen[k]) && (k == m || (abs(chosen[k].first - chosen[k - 1].first) <= RIBBON_STEP && abs(chosen[k].last - chosen[k - 1].last) <= RIBBON_STEP))
            }
            val head = chosen.indices.firstOrNull { anchored(it) } ?: continue
            val tail = chosen.size - 1 - (chosen.indices.last { anchored(it) } + RIBBON_ANCHOR - 1)
            val start = col + head
            val stop = end - tail
            val path = chosen.subList(head, chosen.size - tail)
            val len = path.size
            if (start > edge || stop < n - 1 - edge || len < 3) continue
            // 칸 사이 여백답게 — 짧은 구간이 대부분이고(긴 구간은 엇갈린 칸 사이 세로 여백 몇 곳뿐),
            // 그 위아래 끝(칸 테두리)이 매끈하며, 크게 오르내리지 않는다 (칸 안 빈 곳을 타고 내려가는 길이 아니다)
            if (path.count { short(it) } < len * RIBBON_SHORT_RATIO) continue
            if (path.count { !short(it) } > len * RIBBON_MAX_LONG_RATIO) continue
            if (abs(path.last().first - path.first().first) > len * RIBBON_MAX_SLOPE) continue
            var pairs = 0
            var smooth = 0
            for (m in 1 until len) {
                val a = path[m - 1]
                val b = path[m]
                if (!short(a) || !short(b)) continue
                pairs++
                if (abs(a.first - b.first) <= RIBBON_STEP && abs(a.last - b.last) <= RIBBON_STEP) smooth++
            }
            if (smooth < pairs * RIBBON_SMOOTH_RATIO) continue
            // 열마다 앞·이 열·다음 열이 함께 빈 곳 안에서 뺀다 — 그러면 빼고 나서 이웃 열끼리 어긋나는 곳(두 자리 사이)은
            // 두 열 모두 빈 곳뿐이다. 뺄 줄 수는 그 함께 빈 곳의 가장 좁은 곳
            val low = IntArray(len)
            val high = IntArray(len)
            var rows = Int.MAX_VALUE
            for (m in 0 until len) {
                var a = path[m].first
                var b = path[m].last
                if (m > 0) { a = maxOf(a, path[m - 1].first); b = minOf(b, path[m - 1].last) }
                if (m < len - 1) { a = maxOf(a, path[m + 1].first); b = minOf(b, path[m + 1].last) }
                low[m] = a
                high[m] = b
                rows = minOf(rows, b - a + 1)
            }
            // 여백을 [RIBBON_KEEP]줄 남긴다 — 길 밖 끝 열에서 자리를 옮겨도 이웃 열과 빈 곳끼리 맞닿는다
            rows -= RIBBON_KEEP
            if (rows < minGap) continue
            // 자리는 앞 열 자리에 가장 가깝게(덜 오르내린다), 남기는 줄은 위아래로 나눠 테두리에서 한 줄씩 띄운다
            val lo = IntArray(n)
            val keep = RIBBON_KEEP / 2
            for (m in 0 until len) {
                val prev = if (m > 0) lo[start + m - 1] else low[0] + keep
                lo[start + m] = prev.coerceIn(low[m] + keep, high[m] - rows + 1 - (RIBBON_KEEP - keep))
            }
            // 길 밖 양 끝 열은 길 끝 자리에서 한 열씩 바깥으로, 밋밋하고 이음새가 안 보이는 자리를 [RIBBON_STEP]줄 안에서
            // 가까운 곳부터 골라 따라간다. 쪽 좌우 끝 [scanEdge]열(스캔 가장자리)은 보지 않고 그대로 잇는다
            fun follow(from: Int, until: Int, dir: Int): Boolean {
                var m = from + dir
                while (m != until) {
                    val prev = lo[m - dir]
                    val x = x0 + m
                    if (x < scanEdge || x >= pageWidth - scanEdge) {
                        lo[m] = prev
                        m += dir
                        continue
                    }
                    // 뺄 줄의 위아래 끝 1줄(테두리 가장자리의 옅은 칸)은 보지 않는다
                    fun range(at: Int) = page.rowRange(x, at + 1, at + rows - 1)
                    // 빼고 난 두 열이 자리가 달라 어긋나는 줄에서 밝기가 비슷해야 한다
                    fun out(column: Int, at: Int, y: Int) = page.lumAt(column, if (y < at) y else y + rows)
                    fun seamless(at: Int) = (minOf(prev, at) until maxOf(prev, at)).all { y ->
                        abs(out(x, at, y) - out(x - dir, prev, y)) <= RIBBON_FLAT_DIFF
                    }
                    // 가까운 자리부터
                    val best = (0..RIBBON_STEP).flatMap { d -> if (d == 0) listOf(prev) else listOf(prev - d, prev + d) }
                        .firstOrNull { it >= y0 && it + rows <= y1 && range(it) <= RIBBON_FLAT_DIFF && seamless(it) } ?: return false
                    lo[m] = best
                    m += dir
                }
                return true
            }
            val flat = follow(start, -1, -1) && follow(stop, n, 1)
            if (!flat) continue
            found += last.cost + (n - 1 - stop) * RIBBON_EDGE_COST to Ribbon(x0, lo, rows)
        }
    }
    // 싼 길부터 — 이미 고른 띠와 어느 열에서든 겹치거나 위아래가 엇갈리면 같은 여백을 다르게 지나는 길이라 버린다
    val out = ArrayList<Ribbon>()
    for ((_, ribbon) in found.sortedBy { it.first }) {
        val clear = out.all { other ->
            (0 until n).all { ribbon.start[it] + ribbon.rows <= other.start[it] } ||
                (0 until n).all { other.start[it] + other.rows <= ribbon.start[it] }
        }
        if (clear) out += ribbon
    }
    return out.sortedBy { it.start[0] }
}

/** 바탕 밝기 — 가장 흔한 밝기 (흰 종이면 255 근처, 검은 바탕 쪽이면 0 근처) */
private fun backgroundLuminance(lum: IntArray): Int {
    val hist = IntArray(256)
    for (i in lum.indices step 3) hist[lum[i]]++
    return hist.indices.maxBy { hist[it] }
}

/** 왼쪽·오른쪽 끝에서 이만큼은 보지 않는다 (스캔 테두리) */
private const val EDGE_RATIO = 0.02f

/** 쪽 끝 열에서 쪽 높이의 이만큼보다 길게 이어지는 잉크는 스캔 테두리 */
private const val SCAN_EDGE_RUN_RATIO = 0.6f

/** 바탕과 이만큼 다르면 잉크 */
private const val INK_DIFF = 80

/** 쪽 높이(너비)의 이만큼 이어지는 잉크라야 칸 — 여백의 글자는 이보다 짧다 */
private const val LONG_INK_RATIO = 0.04f

/** 여백을 가로지르는 것이 이 폭(비율)보다 좁으면 글 상자·말풍선으로 본다 */
private const val OVERLAY_SPAN_RATIO = 0.3f

/** 여백 후보의 획이 이만큼 좁은 곳([OVERLAY_SPAN_RATIO] 미만)에 몰려 있으면 걸친 효과음·말풍선 */
private const val OVERLAY_STROKE_SHARE = 0.9

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

/** 가로로 긴 잉크가 줄의 이만큼을 덮으면 칸 테두리가 지나가는 줄 */
private const val BORDER_ROW_RATIO = 0.1f

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

/** 열의 종이색은 쪽 바탕색보다 이만큼까지만 어둡다 (가운데 접힌 곳 그림자) */
private const val MAX_SHADE_DIFF = 100

/** 열의 종이색은 밝은 쪽에서 이 비율 자리의 밝기 */
private const val COLUMN_PAPER_SHARE = 0.95f

/** 비스듬한 여백 띠는 양 끝 이 폭(칸 묶음 폭 비율) 안에서 늦게 시작하거나 일찍 끝나도 된다 (접힌 곳 그림자, 쪽 여백) */
private const val RIBBON_EDGE_RATIO = 0.08f

/** 띠가 양 끝에서 덜 지난 열마다의 값 — 긴 구간을 지나서라도 끝까지 이어진 길을 고른다 */
private const val RIBBON_EDGE_COST = 2000L

/** 비스듬한 여백 띠는 칸 사이를 이만큼 남기고 뺀다 */
private const val RIBBON_KEEP = 2

/** 비스듬한 여백 띠의 양 끝은 짧은 구간이 매끈하게 이만큼 이어지는 곳 */
private const val RIBBON_ANCHOR = 8

/** 띠가 지나지 못한 양 끝 열에서 뺄 자리는 밝기 차이가 이 이하로 밋밋해야 한다 */
private const val RIBBON_FLAT_DIFF = 48

/** 비스듬한 여백 띠는 열의 이만큼 이상이 칸 사이처럼 짧고 막힌 빈 구간이어야 한다 */
private const val RIBBON_SHORT_RATIO = 0.6f

/** 비스듬한 여백 띠가 지나는 긴 구간(엇갈린 칸 사이 세로 여백) 열은 이 비율까지 */
private const val RIBBON_MAX_LONG_RATIO = 0.05f

/** 비스듬한 여백 띠는 왼쪽 끝에서 오른쪽 끝까지 이 기울기보다 덜 오르내린다 (칸 테두리의 기울기) */
private const val RIBBON_MAX_SLOPE = 0.2f

/** 비스듬한 여백 띠의 위아래 끝은 이웃 열끼리 이만큼까지만 달라진다 (칸 테두리의 기울기) */
private const val RIBBON_STEP = 2

/** 이웃 열 끝이 매끈하게([RIBBON_STEP]) 이어지는 비율 — 그림 속 빈 곳은 들쭉날쭉하다 */
private const val RIBBON_SMOOTH_RATIO = 0.8f

/** 비스듬한 여백 띠가 긴 빈 구간(엇갈린 칸의 세로 여백)을 지날 때의 값 — 짧은 구간으로 이어지는 길을 고른다 */
private const val RIBBON_LONG_COST = 1000L

/** 지울 여백이 이보다 적으면 그대로 */
private const val MIN_SAVED_RATIO = 0.005f

/** 페이지 리더(Coil)용 — 작품 보기 설정 "칸 간격 없애기" */
internal class GutterTransformation : Transformation() {
    override val cacheKey: String = "gutters-13"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val out = pageWorkLock.withLock {
            val pixels = IntArray(w * h)
            input.getPixels(pixels, 0, w, 0, 0, w, h)
            collapseGutters(pixels, w, h)
        } ?: return input
        val config = if (input.config == Bitmap.Config.RGB_565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        return createBitmap(w, h, config).apply { setPixels(out, 0, w, 0, 0, w, h) }
    }
}
