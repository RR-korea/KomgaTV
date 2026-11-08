package com.komgatv.feature.reader.enhance

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 확대하면 잘리는 쪽 위·아래 끝의 대사를 쪽 가운데 쪽으로 옮긴다 — 말풍선 안에서, 또는 칸 안 빈 종이 위에서.
 * [moveBalloons]면 잘리는 곳에 걸친 말풍선은 말풍선째로 먼저 민다 ([BalloonPage.moveBalloons]).
 * 그래도 모자라면 말풍선 안에서 대사를 옮긴다.
 *
 * 말풍선: 종이색 덩어리 중 쪽의 [MAX_BALLOON_RATIO] 이하인 것 (쪽 끝에 걸려 열린 말풍선도).
 * 대사: 그 말풍선에만 맞닿은 작은 잉크 덩어리 — 글자 획. 말풍선 테두리는 바깥에도 맞닿아 빠진다.
 * 해설 글: 말풍선이 아닌 큰 종이(칸 안 빈 곳) 위에 모여 있는 글자 획 덩어리 ([BalloonPage.textBlocks]).
 *
 * @param topCut 위에서 잘리는 줄 수
 * @param bottomCut 아래에서 잘리는 줄 수
 * @return 바꾼 픽셀, 옮길 것이 없으면 null
 */
internal fun pullBalloonText(
    pixels: IntArray,
    width: Int,
    height: Int,
    topCut: Int,
    bottomCut: Int,
    moveBalloons: Boolean,
): IntArray? {
    if (width < 8 || height < 32 || (topCut <= 0 && bottomCut <= 0)) return null
    val bg = paperLuminance(pixels)
    if (bg < MIN_PAPER) return null
    // 잘리는 곳 둘레의 위·아래 띠만 본다 (TV 메모리) — 띠가 겹치면 쪽 전체
    val reach = (height * STRIP_RATIO).roundToInt()
    val topEnd = minOf(height, topCut + reach)
    val bottomStart = maxOf(0, height - bottomCut - reach)
    var out: IntArray? = null
    fun strip(y0: Int, y1: Int, top: Int, bottom: Int) {
        val part = pixels.copyOfRange(y0 * width, y1 * width)
        val rows = y1 - y0
        var current = part
        var changed = false
        fun page(px: IntArray) = BalloonPage(px, width, rows, height, bg, innerTop = y0 > 0, innerBottom = y1 < height)
        if (moveBalloons) {
            page(current).moveBalloons(top, bottom)?.let {
                current = it
                changed = true
            }
        }
        page(current).moveText(top, bottom)?.let {
            current = it
            changed = true
        }
        if (changed) System.arraycopy(current, 0, out ?: pixels.copyOf().also { out = it }, y0 * width, current.size)
    }
    if (topEnd >= bottomStart) {
        strip(0, height, topCut, bottomCut)
    } else {
        if (topCut > 0) strip(0, topEnd, topCut, 0)
        if (bottomCut > 0) strip(bottomStart, height, 0, bottomCut)
    }
    return out
}

/** 종이(바탕) 밝기 — 가장 흔한 밝기 (3칸마다 본다) */
private fun paperLuminance(pixels: IntArray): Int {
    val hist = IntArray(256)
    for (i in pixels.indices step 3) {
        val c = pixels[i]
        hist[(((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000]++
    }
    return hist.indices.maxBy { hist[it] }
}

/**
 * 쪽(의 위·아래 띠)의 종이색 덩어리(4방향)와 잉크 덩어리(8방향), 말풍선과 그 안의 대사.
 * 크기 기준은 쪽 높이 [pageHeight]로 잰다. [innerTop]·[innerBottom]은 띠를 자른 곳 — 거기 닿은 말풍선은 크기를 몰라 뺀다.
 */
private class BalloonPage(
    private val pixels: IntArray,
    private val width: Int,
    private val height: Int,
    private val pageHeight: Int,
    private val bg: Int,
    private val innerTop: Boolean,
    private val innerBottom: Boolean,
) {
    private val lum: IntArray
    private val paperLabel: IntArray
    private val inkLabel: IntArray
    private val paperArea: IntArray
    /** 종이 덩어리마다 위·아래·왼쪽·오른쪽 끝 */
    private val paperBox: Array<IntArray>
    private val groups: List<Group>

    /**
     * 종이 덩어리 [balloon] 안의 대사 — 잉크 덩어리들과 그 테두리 상자.
     * [isBalloon]이 아니면 칸 안 빈 종이 위의 해설 글. [canPush]면 제 테두리만 두른 말풍선 — 말풍선째 밀 수 있다.
     */
    class Group(
        val balloon: Int,
        val inks: Set<Int>,
        val top: Int,
        val bottom: Int,
        val left: Int,
        val right: Int,
        val isBalloon: Boolean = true,
        val canPush: Boolean = false,
    )

    private val holeArea = (pageHeight * HOLE_RATIO).let { (it * it).roundToInt() }

    init {
        val lum = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        val paper = BooleanArray(pixels.size) { lum[it] >= bg - PAPER_DIFF }
        // 말풍선은 테두리에서 [CLOSE]칸 넘게 떨어진 종이로 나눈다 — 테두리의 좁은 틈으로 바깥과 이어지지 않게.
        // 테두리 가까운 종이는 가장 가까운 덩어리에 붙인다
        paperLabel = label(eroded(paper, CLOSE), diagonal = false)
        grow(paperLabel, paper)
        inkLabel = label(BooleanArray(pixels.size) { !paper[it] }, diagonal = true)
        this.lum = lum
        paperArea = areas(paperLabel)
        paperBox = boxes(paperLabel, paperArea.size)
        groups = findGroups()
    }

    /** 둘레 [r]칸이 모두 종이인 곳 */
    private fun eroded(mask: BooleanArray, r: Int): BooleanArray {
        val rows = BooleanArray(mask.size)
        for (y in 0 until height) {
            var run = 0
            for (x in 0 until width) {
                run = if (mask[y * width + x]) run + 1 else 0
                // 가로로 [x-2r, x] 모두 종이면 가운데 x-r에 표시
                if (run >= 2 * r + 1) rows[y * width + x - r] = true
            }
        }
        val out = BooleanArray(mask.size)
        for (x in 0 until width) {
            var run = 0
            for (y in 0 until height) {
                run = if (rows[y * width + x]) run + 1 else 0
                if (run >= 2 * r + 1) out[(y - r) * width + x] = true
            }
        }
        return out
    }

    /** 이름표 없는 [mask] 칸을 이웃한 덩어리 이름표로 채워 나간다 (가까운 덩어리부터) */
    private fun grow(labels: IntArray, mask: BooleanArray) {
        val queue = IntArray(labels.size)
        var tail = 0
        for (i in labels.indices) if (labels[i] >= 0) queue[tail++] = i
        var head = 0
        while (head < tail) {
            val i = queue[head++]
            val x = i % width
            val y = i / width
            for ((dx, dy) in NEIGHBOURS) {
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                val j = ny * width + nx
                if (mask[j] && labels[j] < 0) {
                    labels[j] = labels[i]
                    queue[tail++] = j
                }
            }
        }
    }

    private fun label(mask: BooleanArray, diagonal: Boolean): IntArray {
        val labels = IntArray(mask.size) { -1 }
        val queue = IntArray(mask.size)
        var next = 0
        for (start in mask.indices) {
            if (!mask[start] || labels[start] >= 0) continue
            labels[start] = next
            var head = 0
            var tail = 0
            queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                val x = i % width
                val y = i / width
                for (dy in -1..1) for (dx in -1..1) {
                    if ((dx == 0 && dy == 0) || (!diagonal && dx != 0 && dy != 0)) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                    val j = ny * width + nx
                    if (mask[j] && labels[j] < 0) {
                        labels[j] = next
                        queue[tail++] = j
                    }
                }
            }
            next++
        }
        return labels
    }

    private fun areas(labels: IntArray): IntArray {
        val area = IntArray((labels.maxOrNull() ?: -1) + 1)
        for (l in labels) if (l >= 0) area[l]++
        return area
    }

    private fun boxes(labels: IntArray, count: Int): Array<IntArray> {
        val box = Array(count) { intArrayOf(Int.MAX_VALUE, -1, Int.MAX_VALUE, -1) }
        for (i in labels.indices) {
            val l = labels[i]
            if (l < 0) continue
            val x = i % width
            val y = i / width
            val b = box[l]
            if (y < b[0]) b[0] = y
            if (y > b[1]) b[1] = y
            if (x < b[2]) b[2] = x
            if (x > b[3]) b[3] = x
        }
        return box
    }

    /** 말풍선 모양 — 크기가 알맞고, 둘레 상자를 [MIN_BALLOON_FILL] 이상 채운다 (칸 속 그림 사이 빈 곳은 들쭉날쭉하다) */
    private fun isBalloon(paper: Int): Boolean {
        val area = paperArea[paper]
        val pageArea = width.toLong() * pageHeight
        if (area < pageArea * MIN_BALLOON_RATIO || area > pageArea * MAX_BALLOON_RATIO) return false
        val b = paperBox[paper]
        if ((innerTop && b[0] == 0) || (innerBottom && b[1] == height - 1)) return false
        return area >= (b[1] - b[0] + 1).toLong() * (b[3] - b[2] + 1) * MIN_BALLOON_FILL
    }

    /**
     * 말풍선은 테두리 하나(꼬리나 겹친 그림까지 이어진 잉크 덩어리)로 둘러싸여 있다 — 글자 [text] 말고 맞닿은 잉크 덩어리가
     * [MAX_OUTLINE_PARTS]개 이하. 칸 안 빈 곳은 그림 조각, 망점, 칸 테두리 여럿에 맞닿는다.
     * @return 테두리 잉크 덩어리들, 말풍선이 아니면 null
     */
    private fun outline(paper: Int, text: Set<Int>): Set<Int>? {
        val b = paperBox[paper]
        val minContact = (pageHeight * MIN_OUTLINE_CONTACT_RATIO).roundToInt().coerceAtLeast(3)
        val contact = HashMap<Int, Int>()
        val parts = HashSet<Int>()
        for (y in b[0]..b[1]) for (x in b[2]..b[3]) {
            if (paperLabel[y * width + x] != paper) continue
            for ((dx, dy) in NEIGHBOURS) {
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                val ink = inkLabel[ny * width + nx]
                if (ink < 0 || ink in text) continue
                val n = contact.getOrDefault(ink, 0) + 1
                contact[ink] = n
                if (n == minContact) {
                    parts += ink
                    if (parts.size > MAX_OUTLINE_PARTS) return null
                }
            }
        }
        return parts
    }

    /** 글자 속 구멍(ㅇ·ㅁ 안쪽) 같은 작은 종이 덩어리 */
    private fun isHole(paper: Int): Boolean = paperArea[paper] <= holeArea

    private fun findGroups(): List<Group> {
        val inkCount = (inkLabel.maxOrNull() ?: -1) + 1
        // 잉크 덩어리마다 맞닿은 (구멍이 아닌) 종이 덩어리 두 개까지와 그 맞닿은 칸 수
        val touchA = IntArray(inkCount) { -1 }
        val countA = IntArray(inkCount)
        val touchB = IntArray(inkCount) { -1 }
        val countB = IntArray(inkCount)
        val many = BooleanArray(inkCount)
        val top = IntArray(inkCount) { Int.MAX_VALUE }
        val bottom = IntArray(inkCount) { -1 }
        val left = IntArray(inkCount) { Int.MAX_VALUE }
        val right = IntArray(inkCount) { -1 }
        for (i in pixels.indices) {
            val ink = inkLabel[i]
            if (ink < 0) continue
            val x = i % width
            val y = i / width
            top[ink] = minOf(top[ink], y)
            bottom[ink] = maxOf(bottom[ink], y)
            left[ink] = minOf(left[ink], x)
            right[ink] = maxOf(right[ink], x)
            for (dy in -REACH..REACH) for (dx in -REACH..REACH) {
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                val paper = paperLabel[ny * width + nx]
                if (paper < 0 || isHole(paper)) continue
                when {
                    touchA[ink] < 0 || touchA[ink] == paper -> { touchA[ink] = paper; countA[ink]++ }
                    touchB[ink] < 0 || touchB[ink] == paper -> { touchB[ink] = paper; countB[ink]++ }
                    else -> many[ink] = true
                }
            }
        }
        val maxGlyphH = pageHeight * MAX_GLYPH_RATIO
        val maxGlyphW = width * MAX_GLYPH_RATIO
        val byPaper = HashMap<Int, MutableList<Int>>()
        for (ink in 0 until inkCount) {
            // 거의 한 덩어리에만 맞닿아야 대사 — 옆 말풍선 테두리에 살짝 가까운 글자는 괜찮다. 테두리는 안팎에 고루 맞닿는다
            if (touchA[ink] < 0 || many[ink]) continue
            val balloon = when {
                countB[ink] <= countA[ink] * OTHER_TOUCH_RATIO -> touchA[ink]
                countA[ink] <= countB[ink] * OTHER_TOUCH_RATIO -> touchB[ink]
                else -> continue
            }
            if (bottom[ink] - top[ink] + 1 > maxGlyphH || right[ink] - left[ink] + 1 > maxGlyphW) continue
            byPaper.getOrPut(balloon) { mutableListOf() } += ink
        }
        val slack = (pageHeight * RING_RATIO * 2).roundToInt().coerceAtLeast(2)
        val inkArea = areas(inkLabel)
        return byPaper.flatMap { (paper, inks) ->
            val ring = if (isBalloon(paper)) outline(paper, inks.toSet()) else null
            val b = paperBox[paper]
            // 테두리가 말풍선만 두른다 (그림과 이어지지 않았다) — 말풍선째 밀어도 둘레 그림을 끌고 오지 않는다
            val own = ring != null && ring.isNotEmpty() && ring.all {
                top[it] >= b[0] - slack && bottom[it] <= b[1] + slack && left[it] >= b[2] - slack && right[it] <= b[3] + slack
            }
            // 대사는 한데 모여 있다 — 그림 사이 빈 곳의 티끌은 넓게 흩어진다
            val textBox = (inks.maxOf { bottom[it] } - inks.minOf { top[it] } + 1).toLong() *
                (inks.maxOf { right[it] } - inks.minOf { left[it] } + 1)
            val dense = inks.sumOf { inkArea[it] } >= textBox * MIN_TEXT_DENSITY
            // 쪽 끝에 걸린 작은 말풍선 — 큰 것은 쪽 끝까지 이어진 칸 안 빈 곳일 수 있다
            val edge = (b[0] <= EDGE_ROWS || b[1] >= height - 1 - EDGE_ROWS) && b[1] - b[0] + 1 <= pageHeight * MAX_EDGE_BALLOON_RATIO
            if (ring != null && (own || dense)) {
                listOf(
                    Group(
                        paper, inks.toSet(),
                        top = inks.minOf { top[it] }, bottom = inks.maxOf { bottom[it] },
                        left = inks.minOf { left[it] }, right = inks.maxOf { right[it] },
                        // 쪽 끝에 걸린 말풍선은 테두리가 그림과 이어져도 민다 — 끌려오는 쪽 끝은 잘리는 곳 안이다
                        canPush = own || (dense && edge),
                    ),
                )
            } else {
                textBlocks(paper, inks, top, bottom, left, right)
            }
        }.filter { it.bottom - it.top + 1 >= pageHeight * MIN_TEXT_RATIO } // 티끌 하나는 대사가 아니다
    }

    /**
     * 큰 종이 [paper] 위의 글자 크기 잉크 덩어리를 [TEXT_LINK_RATIO] 거리 안끼리 묶어 해설 글 덩어리로.
     * 조각이 여럿이고 그림만큼 크지 않으며, 둘레 상자 안에 다른 잉크(그림)가 없는 것만 — 그림에 붙은 글자를 두고 가지 않게.
     */
    private fun textBlocks(paper: Int, inks: List<Int>, top: IntArray, bottom: IntArray, left: IntArray, right: IntArray): List<Group> {
        val link = (pageHeight * TEXT_LINK_RATIO).roundToInt().coerceAtLeast(2)
        val minPart = (pageHeight * MIN_GLYPH_PART_RATIO).roundToInt().coerceAtLeast(2)
        fun size(ink: Int) = maxOf(bottom[ink] - top[ink], right[ink] - left[ink]) + 1
        // 글자 획만 잇는다 — 망점 같은 티끌로 이어 붙이면 글과 그림이 한 덩어리가 된다
        val (parts, specks) = inks.partition { size(it) >= minPart }
        val parent = IntArray(parts.size) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            var j = i
            while (parent[j] != r) j = parent[j].also { parent[j] = r }
            return r
        }
        // 가까운 것끼리 — 격자 칸에 넣어 같은 칸에 든 것만 견준다
        val cell = link * 4
        val grid = HashMap<Long, MutableList<Int>>()
        for ((n, ink) in parts.withIndex()) {
            for (cy in (top[ink] - link).coerceAtLeast(0) / cell..(bottom[ink] + link) / cell) {
                for (cx in (left[ink] - link).coerceAtLeast(0) / cell..(right[ink] + link) / cell) {
                    val list = grid.getOrPut(cy.toLong() shl 32 or cx.toLong()) { mutableListOf() }
                    for (m in list) {
                        val o = parts[m]
                        val near = left[ink] - link <= right[o] && left[o] <= right[ink] + link &&
                            top[ink] - link <= bottom[o] && top[o] <= bottom[ink] + link
                        if (near) parent[find(n)] = find(m)
                    }
                    list += n
                }
            }
        }
        val maxH = pageHeight * MAX_TEXT_BLOCK_H_RATIO
        val maxW = pageHeight * MAX_TEXT_BLOCK_W_RATIO
        return parts.indices.groupBy { find(it) }.values.mapNotNull { members ->
            if (members.size < MIN_TEXT_PARTS) return@mapNotNull null
            val block = members.map { parts[it] }.toMutableSet()
            val t = block.minOf { top[it] }
            val b = block.maxOf { bottom[it] }
            val l = block.minOf { left[it] }
            val r = block.maxOf { right[it] }
            if (b - t + 1 > maxH || r - l + 1 > maxW) return@mapNotNull null
            // 글 줄 높이 안, 글 옆 [minPart] 안의 티끌(마침표, 쉼표)은 함께 옮긴다
            for (ink in specks) {
                if (top[ink] >= t && bottom[ink] <= b && left[ink] >= l - minPart && right[ink] <= r + minPart) block += ink
            }
            val g = Group(
                paper, block,
                top = t, bottom = b, left = block.minOf { left[it] }, right = block.maxOf { right[it] },
                isBalloon = false,
            )
            // 둘레 상자 안에 통째로 든 다른 잉크 — 그림에 붙어 못 옮기는 글자일 수 있다. 상자 밖에서 들어온 그림, 티끌은 괜찮다
            val foreign = HashSet<Int>()
            for (y in g.top..g.bottom) for (x in g.left..g.right) {
                val ink = inkLabel[y * width + x]
                if (ink >= 0 && ink !in g.inks) foreign += ink
            }
            val stray = foreign.any {
                top[it] >= g.top && bottom[it] <= g.bottom && left[it] >= g.left && right[it] <= g.right && size(it) >= minPart
            }
            if (stray) null else g
        }
    }


    /** 대사가 잘리는 곳에 걸렸으면 잘리지 않을 만큼 옮길 거리 (아래로 +), 아니면 0 */
    private fun wanted(group: Group, topCut: Int, bottomCut: Int): Int {
        val margin = (pageHeight * MARGIN_RATIO).roundToInt()
        val visibleBottom = height - bottomCut
        val upper = group.top < topCut
        val lower = group.bottom >= visibleBottom
        return when {
            upper && lower -> 0
            upper -> topCut - group.top + margin
            lower -> visibleBottom - 1 - group.bottom - margin
            else -> 0
        }
    }

    /** 대사 자리: 말풍선 안, 대사 획(과 [marks]), 대사 상자 안의 구멍 */
    private fun inside(group: Group, i: Int, marks: Set<Int> = emptySet()): Boolean {
        val paper = paperLabel[i]
        if (paper == group.balloon || i in marks) return true
        val ink = inkLabel[i]
        if (ink >= 0) return ink in group.inks
        val x = i % width
        val y = i / width
        // 글자 속 구멍 — 작은 덩어리이거나, 너무 좁아 덩어리를 못 이룬 종이
        return (paper < 0 || isHole(paper)) && y in group.top..group.bottom && x in group.left..group.right
    }

    /** 대사 획과 그 둘레 [HALO]칸 안의 옅은 회색(종이보다 [HALO_DIFF] 넘게 어두운 곳) — 옮길 것 */
    private fun marks(group: Group): Set<Int> {
        val marks = HashSet<Int>()
        for (y in group.top..group.bottom) for (x in group.left..group.right) {
            val i = y * width + x
            if (inkLabel[i] < 0 || inkLabel[i] !in group.inks) continue
            marks += i
            for (yy in (y - HALO).coerceAtLeast(0)..(y + HALO).coerceAtMost(height - 1)) {
                for (xx in (x - HALO).coerceAtLeast(0)..(x + HALO).coerceAtMost(width - 1)) {
                    val j = yy * width + xx
                    val paper = paperLabel[j]
                    if (lum[j] < bg - HALO_DIFF && (paper == group.balloon || paper < 0 || isHole(paper))) marks += j
                }
            }
        }
        return marks
    }

    /**
     * 대사 줄들 — 대사 획이 있는 줄이 이어진 곳. [minGap]보다 짧은 빈 줄(글자 속 틈)은 줄 안으로 친다.
     * @return 줄마다 첫 줄·끝 줄
     */
    private fun textLines(group: Group, minGap: Int): List<IntRange> {
        val inked = BooleanArray(group.bottom - group.top + 1)
        for (y in group.top..group.bottom) for (x in group.left..group.right) {
            if (inkLabel[y * width + x] in group.inks) {
                inked[y - group.top] = true
                break
            }
        }
        val lines = ArrayList<IntRange>()
        var start = -1
        var last = -1
        for (k in inked.indices) {
            if (!inked[k]) continue
            if (start < 0) {
                start = k
            } else if (k - last - 1 >= minGap) {
                lines += (group.top + start)..(group.top + last)
                start = k
            }
            last = k
        }
        if (start >= 0) lines += (group.top + start)..(group.top + last)
        return lines
    }

    /**
     * 대사 줄 사이를 [keep] 비율로 좁힐 때 줄마다 더 옮길 거리 (가운데 쪽으로 +) — 잘리는 쪽에서 먼 줄은 그대로,
     * 잘리는 쪽 줄일수록 좁힌 만큼 가운데 쪽으로 다가간다. 줄 사이는 [minGap]보다 좁히지 않는다.
     */
    private fun lineShifts(lines: List<IntRange>, keep: Float, minGap: Int, down: Boolean): IntArray {
        val shifts = IntArray(lines.size)
        if (down) {
            for (k in lines.size - 2 downTo 0) {
                val gap = lines[k + 1].first - lines[k].last - 1
                shifts[k] = shifts[k + 1] + (gap - maxOf(minGap, (gap * keep).roundToInt())).coerceAtLeast(0)
            }
        } else {
            for (k in 1 until lines.size) {
                val gap = lines[k].first - lines[k - 1].last - 1
                shifts[k] = shifts[k - 1] + (gap - maxOf(minGap, (gap * keep).roundToInt())).coerceAtLeast(0)
            }
        }
        return shifts
    }

    fun moveText(topCut: Int, bottomCut: Int): IntArray? {
        var out: IntArray? = null
        val gap = (pageHeight * GAP_RATIO).roundToInt().coerceAtLeast(2)
        val lineGap = (pageHeight * LINE_GAP_RATIO).roundToInt().coerceAtLeast(2)
        val minLineGap = (pageHeight * MIN_LINE_GAP_RATIO).roundToInt().coerceAtLeast(2)
        for (group in groups) {
            val want = wanted(group, topCut, bottomCut)
            if (want == 0) continue
            val marks = marks(group)
            val strokes = marks.filter { inkLabel[it] >= 0 }
            val down = want > 0
            val lines = textLines(group, lineGap)
            // 줄마다 그 줄 획(과 둘레)의 줄 — 줄 사이 빈 곳의 옅은 테두리는 가까운 줄을 따라간다
            fun lineOf(y: Int): Int {
                var best = 0
                for (k in lines.indices) {
                    val dist = if (y < lines[k].first) lines[k].first - y else if (y > lines[k].last) y - lines[k].last else 0
                    val bestDist = if (y < lines[best].first) lines[best].first - y else if (y > lines[best].last) y - lines[best].last else 0
                    if (dist < bestDist) best = k
                }
                return best
            }
            val rowLine = IntArray(group.bottom - group.top + 1 + 2 * HALO) { lineOf(group.top - HALO + it) }
            fun ok(x: Int, y: Int) = x in 0 until width && y in 0 until height && inside(group, y * width + x, marks)
            // 옮긴 획은 말풍선 안에 — 테두리와 [gap]칸 띄우되, 지금 이미 그보다 가까운 쪽은 더 가까워지지만 않게
            fun clear(x: Int, y: Int, sx: Int, sy: Int) = ok(x, y) || !ok(sx, sy)
            fun fits(d: Int, shifts: IntArray): Boolean = strokes.all { i ->
                val sx = i % width
                val sy = i / width
                val y = sy + d + (if (down) 1 else -1) * shifts[rowLine[sy - group.top + HALO]]
                ok(sx, y) && clear(sx, y - gap, sx, sy - gap) && clear(sx, y + gap, sx, sy + gap) &&
                    clear(sx - gap, y, sx - gap, sy) && clear(sx + gap, y, sx + gap, sy)
            }
            // 줄 사이를 그대로 두고, 모자라면 조금씩 좁혀 가며 — 잘리는 쪽 첫 줄이 가장 멀리 가는 것.
            // 말풍선 안 대사는 말풍선의 쪽 가운데 쪽 끝까지 붙인다 (위쪽 말풍선이면 아래로). 칸 안 해설 글은 필요한 만큼만
            val step = if (down) 1 else -1
            val box = paperBox[group.balloon]
            val far = when {
                !group.isBalloon -> 0
                down -> box[1] - group.bottom
                else -> group.top - box[0]
            }
            var best: Pair<Int, IntArray>? = null
            var bestReach = 0
            for (keep in LINE_GAP_KEEP) {
                val shifts = lineShifts(lines, keep, minLineGap, down)
                val extra = shifts.maxOrNull() ?: 0
                if (keep < 1f && extra == 0) break
                // 잘리는 쪽 첫 줄은 [extra]만큼 더 간다 — 모두 합쳐 [want]만큼이면 된다
                val need = maxOf(abs(want) - extra, if (keep == 1f) far else 0, 0)
                var d = step * need
                while (d != 0 && !fits(d, shifts)) d -= step
                val reach = abs(d) + extra
                if (reach < MIN_MOVE) continue
                if (reach > bestReach) {
                    best = d to shifts
                    bestReach = reach
                }
                if (reach >= abs(want)) break
            }
            val (d, shifts) = best ?: continue
            if (bestReach < MIN_MOVE) continue
            val dst = out ?: pixels.copyOf().also { out = it }
            val paperColor = averagePaper(group)
            for (i in marks) dst[i] = paperColor
            for (i in marks) {
                val move = d + step * shifts[rowLine[i / width - group.top + HALO]]
                dst[i + move * width] = pixels[i]
            }
        }
        return out
    }


    /**
     * 잘리는 곳에 걸친 말풍선을 쪽 끝 쪽 그림째로 가운데 쪽으로 민다 — 위쪽 말풍선이면 말풍선 아래 끝까지의 열을 아래로.
     * 쪽 끝 쪽에서 끌려오는 것(쪽 끝의 빈 줄, 칸 테두리)은 잘리는 곳 안에 남을 만큼만 밀어 보이지 않고,
     * 말풍선은 바로 아래 그림을 조금 덮는다. 말풍선 좌우 [TAPER_RATIO]만큼은 미는 거리를 줄여 가며 이어 붙인다.
     */
    fun moveBalloons(topCut: Int, bottomCut: Int): IntArray? {
        var out: IntArray? = null
        val ring = (pageHeight * RING_RATIO).roundToInt().coerceAtLeast(2)
        val taper = (width * TAPER_RATIO).roundToInt().coerceAtLeast(1)
        for (group in groups) {
            if (!group.canPush) continue
            val want = wanted(group, topCut, bottomCut)
            if (want == 0) continue
            val box = paperBox[group.balloon]
            val down = want > 0
            // 말풍선 바깥 끝(테두리 포함)이 잘리는 곳 안에 남을 만큼만 — 그 너머에서 끌려오는 그림이 보이지 않게
            val hidden = if (down) topCut - (box[0] - ring) else box[1] + ring - (height - bottomCut) + 1
            val d = if (down) minOf(want, topCut, hidden) else -minOf(-want, bottomCut, hidden)
            if (abs(d) < MIN_MOVE) continue
            // 열마다 말풍선(테두리 포함) 끝 줄
            val edge = IntArray(width) { -1 }
            for (y in box[0]..box[1]) for (x in box[2]..box[3]) {
                if (paperLabel[y * width + x] != group.balloon) continue
                edge[x] = if (edge[x] < 0) y else if (down) maxOf(edge[x], y) else minOf(edge[x], y)
            }
            val src = out ?: pixels
            val dst = out ?: pixels.copyOf().also { out = it }
            val before = src.copyOf()
            for (x in (box[2] - taper).coerceAtLeast(0)..(box[3] + taper).coerceAtMost(width - 1)) {
                val inner = x.coerceIn(box[2], box[3])
                val far = abs(x - inner)
                val shift = (d * (1f - far.toFloat() / (taper + 1))).roundToInt()
                if (shift == 0) continue
                var e = edge[inner]
                if (e < 0) e = if (down) box[1] else box[0]
                e = if (down) (e + ring).coerceAtMost(height - 1) else (e - ring).coerceAtLeast(0)
                if (down) {
                    for (y in (e + shift).coerceAtMost(height - 1) downTo 0) {
                        val from = y - shift
                        dst[y * width + x] = before[from.coerceAtLeast(0) * width + x]
                    }
                } else {
                    for (y in (e + shift).coerceAtLeast(0) until height) {
                        val from = y - shift
                        dst[y * width + x] = before[from.coerceAtMost(height - 1) * width + x]
                    }
                }
            }
        }
        return out
    }

    /** 대사 둘레 말풍선 종이색의 평균 (글자의 옅은 테두리는 빼고) */
    private fun averagePaper(group: Group): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        val pad = HALO * 4
        for (y in (group.top - pad).coerceAtLeast(0)..(group.bottom + pad).coerceAtMost(height - 1)) {
            for (x in (group.left - pad).coerceAtLeast(0)..(group.right + pad).coerceAtMost(width - 1)) {
                val i = y * width + x
                if (paperLabel[i] != group.balloon || lum[i] < bg - HALO_DIFF) continue
                val c = pixels[i]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
            }
        }
        if (n == 0) return pixels[group.top * width + group.left]
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
}

private val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

/** 잘리는 줄에서 쪽 가운데 쪽으로 이만큼(쪽 높이 비율)까지의 띠만 본다 — 말풍선이 그 안에 들어 있어야 한다 */
private const val STRIP_RATIO = 0.25f

/** 바탕(종이)이 이보다 어두우면 (검은 바탕 쪽) 손대지 않는다 */
private const val MIN_PAPER = 128

/** 종이색과 이만큼 안쪽이면 종이, 더 어두우면 잉크 */
private const val PAPER_DIFF = 60

/** 글자 둘레에서 종이색보다 이만큼 어두운 곳은 글자의 옅은 테두리 */
private const val HALO_DIFF = 6

/** 말풍선 테두리의 이 칸 수만 한 틈은 막힌 것으로 본다 */
private const val CLOSE = 2

/** 잉크 덩어리가 맞닿은 종이를 이 거리까지 본다 */
private const val REACH = 1

/** 말풍선은 둘레 상자를 이만큼은 채운다 (타원은 0.79) */
private const val MIN_BALLOON_FILL = 0.5f

/** 말풍선 종이에 맞닿은 (글자 아닌) 잉크 덩어리는 이만큼(테두리, 꼬리가 이어진 그림)까지 */
private const val MAX_OUTLINE_PARTS = 2

/** 말풍선 종이에 이 칸 수(쪽 높이 비율)보다 적게 맞닿은 잉크 티끌은 세지 않는다 */
private const val MIN_OUTLINE_CONTACT_RATIO = 0.005f

/** 대사 덩어리는 쪽 높이의 이만큼은 된다 */
private const val MIN_TEXT_RATIO = 0.01f

/** 말풍선은 쪽 넓이의 이만큼 이상, [MAX_BALLOON_RATIO] 이하 */
private const val MIN_BALLOON_RATIO = 0.001
private const val MAX_BALLOON_RATIO = 0.06

/** 대사 획이 다른 종이 덩어리에 맞닿은 칸이 주 덩어리의 이 비율 이하면 주 덩어리의 대사 */
private const val OTHER_TOUCH_RATIO = 0.25f

/** 글자 획 덩어리는 쪽 높이(너비)의 이만큼보다 작다 */
private const val MAX_GLYPH_RATIO = 0.08f

/** 이 크기(쪽 높이 비율)의 정사각형보다 작은 종이 덩어리는 글자 속 구멍 */
private const val HOLE_RATIO = 0.015f

/** 옮긴 대사가 잘리는 선에서 띄울 거리 (쪽 높이 비율) */
private const val MARGIN_RATIO = 0.01f

/** 옮긴 대사와 말풍선 테두리 사이 거리 (쪽 높이 비율) */
private const val GAP_RATIO = 0.004f

/** 대사 글자 획은 글 상자를 이만큼은 채운다 */
private const val MIN_TEXT_DENSITY = 0.1f

/** 말풍선이 쪽 끝에서 이 줄 수 안까지 닿으면 쪽 끝에 걸린 말풍선 */
private const val EDGE_ROWS = 2

/** 테두리가 그림과 이어진 채 쪽 끝에 걸린 말풍선은 쪽 높이의 이만큼까지만 민다 */
private const val MAX_EDGE_BALLOON_RATIO = 0.12f

/** 해설 글: 글자 조각끼리 이 거리(쪽 높이 비율) 안이면 한 덩어리 */
private const val TEXT_LINK_RATIO = 0.012f

/** 해설 글 덩어리는 조각이 이만큼은 있다 (글자 몇 개) */
private const val MIN_TEXT_PARTS = 3

/** 이보다 작은 잉크(쪽 높이 비율)는 티끌 — 해설 글 상자 안에 남아도 괜찮다 */
private const val MIN_GLYPH_PART_RATIO = 0.005f

/** 해설 글 덩어리는 쪽 높이의 이만큼보다 낮고 좁다 — 더 크면 그림(군중, 풀숲) */
private const val MAX_TEXT_BLOCK_H_RATIO = 0.3f
private const val MAX_TEXT_BLOCK_W_RATIO = 0.4f

/** 말풍선을 밀 때 좌우로 미는 거리를 줄여 가며 이어 붙이는 폭 (쪽 너비 비율) */
private const val TAPER_RATIO = 0.03f

/** 말풍선 테두리 두께로 볼 거리 (쪽 높이 비율) */
private const val RING_RATIO = 0.006f

/** 글자 둘레의 옅은 테두리(안티에일리어싱)까지 함께 옮긴다 */
private const val HALO = 4

/** 대사 줄 사이로 볼 가장 짧은 빈 줄 (쪽 높이 비율) — 이보다 짧으면 글자 속 틈 */
private const val LINE_GAP_RATIO = 0.004f

/** 대사 줄 사이를 좁혀도 이만큼(쪽 높이 비율)은 남긴다 */
private const val MIN_LINE_GAP_RATIO = 0.003f

/** 대사가 다 안 옮겨지면 줄 사이를 이 비율들로 차례로 좁혀 본다 (1 = 그대로) */
private val LINE_GAP_KEEP = floatArrayOf(1f, 0.7f, 0.45f, 0.2f)

/** 이보다 조금 옮기는 건 하지 않는다 */
private const val MIN_MOVE = 2

/**
 * 쪽 전체를 훑는 무거운 처리(칸 간격, 대사 옮기기)는 한 번에 한 쪽씩 — 미리 받는 쪽들이 한꺼번에 돌면
 * 큰 배열이 겹쳐 TV 메모리가 모자란다.
 */
internal val pageWorkLock = Mutex()

/**
 * 확대([zoomPercent])했을 때 쪽 위·아래에서 잘리는 줄 수 — 화면 높이 [screenHeightPx]에 크기 맞춤으로 그린 [width]×[height] 쪽.
 * 확대는 화면 가운데 기준이라 위아래가 같다 (▲▼로 옮긴 자리는 보지 않는다 — 옮길 때마다 쪽을 다시 그리지 않게).
 * [fitHeight]면 높이 맞춤, 아니면 칸([slotWidthPx]×[screenHeightPx]) 안에 맞춤.
 */
internal fun zoomCutRows(
    width: Int,
    height: Int,
    zoomPercent: Int,
    slotWidthPx: Int,
    screenHeightPx: Int,
    fitHeight: Boolean,
): Int {
    if (zoomPercent <= 100 || width <= 0 || height <= 0) return 0
    val scale = if (fitHeight) screenHeightPx.toFloat() / height else minOf(slotWidthPx.toFloat() / width, screenHeightPx.toFloat() / height)
    val letterbox = (screenHeightPx - height * scale) / 2
    val screenCut = screenHeightPx * (1f - 100f / zoomPercent) / 2
    return ((screenCut - letterbox) / scale).roundToInt().coerceAtLeast(0)
}

/** 페이지 리더(Coil)용 — 확대하면 잘리는 쪽 끝 대사를 옮긴다 (작품 보기 설정 "확대", "말풍선까지 옮기기") */
internal class BalloonTextTransformation(
    private val zoomPercent: Int,
    private val moveBalloons: Boolean,
    private val slotWidthPx: Int,
    private val screenHeightPx: Int,
    private val fitHeight: Boolean,
) : Transformation() {
    override val cacheKey: String = "balloons-4-$zoomPercent-$moveBalloons-${slotWidthPx}x$screenHeightPx-$fitHeight"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val cut = zoomCutRows(w, h, zoomPercent, slotWidthPx, screenHeightPx, fitHeight)
        if (cut <= 0) return input
        val out = pageWorkLock.withLock {
            val pixels = IntArray(w * h)
            input.getPixels(pixels, 0, w, 0, 0, w, h)
            pullBalloonText(pixels, w, h, cut, cut, moveBalloons)
        } ?: return input
        val config = if (input.config == Bitmap.Config.RGB_565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        return createBitmap(w, h, config).apply { setPixels(out, 0, w, 0, 0, w, h) }
    }
}
