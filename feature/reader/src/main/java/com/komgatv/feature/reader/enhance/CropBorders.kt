package com.komgatv.feature.reader.enhance

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import com.komgatv.core.model.CropBorders
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.ceil

/**
 * 쪽 둘레의 빈 테두리(스캔 여백) 두께 — 종이가 흰색이든 검은색이든 둘레의 색으로 본다.
 *
 * 변마다 맨 바깥 줄에서 가장 흔한 밝기를 여백 색으로 삼고(밝으면 [MIN_LIGHT_BORDER] 이상, 어두우면 [MAX_DARK_BORDER] 이하만),
 * 열(좌우 변이면 줄)마다 쪽 끝에서 안쪽으로 여백 색([BORDER_TOLERANCE] 안)이 몇 픽셀 이어지는지 잰다.
 * 위아래: 칸 테두리 밖으로 그림이 나온 열(쪽 끝까지 그린 칸, 칸 밖 머리·옷자락)이나 쪽 번호가 있는 열은 여백이 얕지만,
 * 테두리가 있는 열들에 맞춘다 — 열 중 [COVER_RATIO]가 그만큼 이상 여백인 가장 깊은 곳까지가 테두리다.
 * 그래서 칸 밖으로 나온 그림 끝과 쪽 번호는 테두리와 함께 잘린다. 그림이 쪽 끝까지 꽉 찬 변은 여백이 없어 그대로다.
 * 단 그 깊이에 칸 테두리 선이 있을 때만 — 테두리 없이 열린 칸(머리카락·하늘), 칸 밖 글 문단에서는 모든 열이 여백인 곳까지만.
 * 좌우: 거의 모든 줄([STRICT_COVER_RATIO])이 여백인 곳까지만 — 칸 밖으로 나온 그림은 자르지 않는다.
 *
 * 검은 테두리는 네 변 중 셋 이상의 바깥 줄이 검을 때만 본다 (검은 바탕에 스캔한 쪽) — 흰 종이 쪽 아래 끝까지
 * 이어진 검은 밤하늘 같은 그림은 테두리가 아니다. 흰 종이 쪽 끝의 얇은 검은 스캔 선([THIN_EDGE_RATIO] 안)은 건너
 * 그 안쪽 흰 여백을 잰다. 한 변에서 쪽의 [MAX_CROP_RATIO]까지만 잰다.
 *
 * @param topBottom 위아래를 볼지
 * @param leftRight 좌우를 볼지 (위아래도 보면 위아래 테두리를 뺀 줄 사이에서만)
 * @return 변마다 테두리 줄 수 [위, 아래, 왼쪽, 오른쪽], 모두 거의 없으면 null
 */
internal fun findBorders(pixels: IntArray, width: Int, height: Int, topBottom: Boolean, leftRight: Boolean): IntArray? {
    if (width < MIN_SIDE || height < MIN_SIDE) return null
    val lum = IntArray(pixels.size) { luminanceOf(pixels[it]) }
    val hist = IntArray(256)

    /** 첫 픽셀 [start]부터 [step] 간격 [count]개 픽셀 중 가장 흔한 밝기 */
    fun commonLevel(start: Int, step: Int, count: Int): Int {
        hist.fill(0)
        var i = start
        repeat(count) {
            hist[lum[i]]++
            i += step
        }
        var peak = 0
        for (v in 1 until 256) if (hist[v] > hist[peak]) peak = v
        return peak
    }

    fun isDark(level: Int) = level <= MAX_DARK_BORDER

    /** 맨 바깥 줄과 그 안쪽 줄들 중 더 어두운 레벨 (가장자리 빛샘·노이즈 방어) */
    fun edgeLevel(start: Int, step: Int, count: Int, inward: Int): Int {
        val l0 = commonLevel(start, step, count)
        val l1 = commonLevel(start + inward, step, count)
        val l2 = commonLevel(start + 2 * inward, step, count)
        val minLevel = minOf(l0, l1, l2)
        return if (isDark(minLevel)) minLevel else l0
    }

    // 네 변 중 셋 이상의 바깥 레벨이 검거나, 둘 이상이 짙은 검은색이면서 흰 종이(MIN_LIGHT_BORDER 이상) 변이 없으면 검은 바탕 쪽
    val edgeLevels = listOf(
        edgeLevel(0, 1, width, width),
        edgeLevel((height - 1) * width, 1, width, -width),
        edgeLevel(0, width, height, 1),
        edgeLevel(width - 1, width, height, -1),
    )
    val darkPage = edgeLevels.count { isDark(it) } >= 3 ||
        (edgeLevels.count { it <= 50 } >= 2 && edgeLevels.all { it < MIN_LIGHT_BORDER } && edgeLevels.count { it <= 110 } >= 3)

    /**
     * 한 변의 테두리 두께. 변을 따라 [count]개 선(열 또는 줄)이 있고, k번째 선의 쪽 끝 픽셀은 [first] + k×[across],
     * 안쪽으로 한 픽셀 갈 때마다 [inward]만큼 움직인다. [depthLimit]까지, 변에 수직인 길이 [length].
     * [strict]: 좌우 변처럼 칸 밖으로 나온 그림을 자르지 않아야 할 때 true (1줄 먼지 완화 후 최소 여백 깊이)
     */
    fun sideDepth(first: Int, across: Int, count: Int, inward: Int, length: Int, depthLimit: Int, cover: Float, strict: Boolean = false): Int {
        if (count <= 0) return 0
        // 흰 종이 쪽 끝의 얇은 검은 스캔 선 또는 검은 바탕 가장자리 빛샘 건넌다
        var skip = 0
        var level = commonLevel(first, across, count)
        if (darkPage) {
            val l1 = commonLevel(first + inward, across, count)
            val l2 = commonLevel(first + 2 * inward, across, count)
            if (isDark(l1) && (!isDark(level) || l1 + BORDER_TOLERANCE < level)) {
                skip = 1
                level = l1
            } else if (isDark(l2) && (!isDark(level) || l2 + BORDER_TOLERANCE < level)) {
                skip = 2
                level = l2
            }
        } else if (isDark(level) && !darkPage) {
            skip = ceil(length * THIN_EDGE_RATIO).toInt()
            level = commonLevel(first + skip * inward, across, count)
        }
        val margin = level >= MIN_LIGHT_BORDER || (isDark(level) && darkPage)
        if (!margin) return 0
        val depths = IntArray(count) { k ->
            val edge = first + k * across
            var d = skip
            while (d < depthLimit && abs(lum[edge + d * inward] - level) <= BORDER_TOLERANCE) d++
            d
        }
        /** 모든 선이 여백인 깊이 — 1줄짜리 고립된 먼지 점(안쪽 1~2px 가 다시 여백인 점)만 이웃 선으로 완화하고, 안쪽으로 이어지는 펜선(머리카락 끝·칼끝)은 보존 */
        fun strictDepth(): Int {
            fun isDust(i: Int, d: Int): Boolean {
                if (d + 2 >= depthLimit) return false
                val edge = first + i * across
                val step1 = abs(lum[edge + (d + 1) * inward] - level) > BORDER_TOLERANCE
                val step2 = abs(lum[edge + (d + 2) * inward] - level) > BORDER_TOLERANCE
                return if (step1) {
                    !step2 && (d + 3 >= depthLimit || abs(lum[edge + (d + 3) * inward] - level) <= BORDER_TOLERANCE)
                } else {
                    !step2
                }
            }
            val smoothed = IntArray(count) { i ->
                val prev = if (i > 0) depths[i - 1] else depths[i]
                val next = if (i < count - 1) depths[i + 1] else depths[i]
                if (depths[i] < prev && depths[i] < next && isDust(i, depths[i])) {
                    maxOf(depths[i], minOf(prev, next))
                } else {
                    depths[i]
                }
            }
            return smoothed.minOrNull() ?: 0
        }

        /** k번째 선이 [d] 깊이에서 멈춘 픽셀이 변을 따라 길게 이어진 잉크(칸 테두리 선)에 들어 있는지 */
        fun stopsAtLine(k: Int, d: Int): Boolean {
            if (d >= depthLimit) return false
            fun inked(j: Int) = abs(lum[first + j * across + d * inward] - level) > BORDER_TOLERANCE
            var a = k
            var b = k
            while (a > 0 && inked(a - 1)) a--
            while (b < count - 1 && inked(b + 1)) b++
            return b - a + 1 >= count * BORDER_LINE_RATIO
        }

        val depth = if (strict) {
            strictDepth()
        } else {
            // 위아래 변: 선 중 [cover]가 이만큼 이상 여백인 깊이 (칸 밖 머리·옷자락·쪽 번호 무시) —
            // 단 그 깊이에서 여러 열([LINE_SUPPORT_RATIO])이 함께 칸 테두리 선에 닿을 때만. 테두리 없는 칸(하늘·머리카락이
            // 쪽 끝으로 열린 칸, 칸 밖 글 문단)이면 모든 열이 여백인 곳까지만 자른다
            val sorted = depths.sortedArrayDescending()
            val candidate = sorted[(count * cover).toInt().coerceAtMost(count - 1)]
            val tolerance = maxOf(2, (length * LINE_DEPTH_TOLERANCE).toInt())
            var onLine = 0
            for (k in 0 until count) {
                if (abs(depths[k] - candidate) <= tolerance && stopsAtLine(k, depths[k])) onLine++
            }
            if (onLine >= count * LINE_SUPPORT_RATIO) candidate else strictDepth()
        }
        // 아주 얇은 것은 그림 결이다 (쪽 끝까지 그린 그림의 잔 무늬)
        return if (depth > skip && depth >= length * MIN_BORDER_RATIO) depth else 0
    }

    val maxY = (height * MAX_CROP_RATIO).toInt()
    val maxX = (width * MAX_CROP_RATIO).toInt()
    var top = 0
    var bottom = 0
    if (topBottom) {
        top = sideDepth(0, 1, width, width, height, maxY, COVER_RATIO, strict = false)
        bottom = sideDepth((height - 1) * width, 1, width, -width, height, maxY, COVER_RATIO, strict = false)
    }
    var left = 0
    var right = 0
    if (leftRight) {
        // 위아래 여백(쪽 번호 등)을 뺀 줄 사이에서만 본다
        val rows = height - top - bottom
        left = sideDepth(top * width, width, rows, 1, width, maxX, STRICT_COVER_RATIO, strict = true)
        right = sideDepth(top * width + width - 1, width, rows, -1, width, maxX, STRICT_COVER_RATIO, strict = true)
    }
    if (top + bottom < height * MIN_CROP_RATIO && left + right < width * MIN_CROP_RATIO) return null
    return intArrayOf(top, bottom, left, right)
}

/**
 * 자를 비율 [위, 아래, 바깥, 안쪽] — 바깥은 화면 가장자리 쪽 변, 안쪽은 펼침 가운데(다른 쪽에 닿는) 변.
 * 두 쪽 보기에서 다른 쪽([partner])의 테두리가 더 두꺼우면 그만큼 이 쪽도 잘라 두 쪽을 맞춘다 (바깥은 바깥끼리, 안쪽은 안쪽끼리).
 */
internal fun matchCrop(own: FloatArray, partner: FloatArray?): FloatArray =
    FloatArray(4) { maxOf(own[it], partner?.get(it) ?: 0f) }

private fun luminanceOf(c: Int) = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000

/** 펼침의 쪽마다 잰 테두리 비율 — 같은 펼침의 다른 쪽이 읽어 맞춘다 (쪽 주소|설정 → [위, 아래, 바깥, 안쪽]) */
private val spreadBorders = ConcurrentHashMap<String, FloatArray>()

/** 이보다 작은 그림은 보지 않는다 */
private const val MIN_SIDE = 16

/** 여백 색의 밝기 폭 — 종이 결, 스캔 잡티, JPEG 얼룩 */
private const val BORDER_TOLERANCE = 40

/** 위아래 변의 열 중 이만큼이 그 깊이까지 여백이면 거기까지가 테두리 — 나머지 열은 칸 밖으로 나온 그림·쪽 번호 */
private const val COVER_RATIO = 0.3f

/** 위아래 변에서 칸 테두리 선으로 칠 가로 잉크의 길이 — 변 길이의 이만큼 (글자 획·머리카락은 짧다) */
private const val BORDER_LINE_RATIO = 0.08f

/** 고른 깊이에서 테두리 선에 닿은 열이 이만큼은 돼야 테두리에 맞춰 자른다 */
private const val LINE_SUPPORT_RATIO = 0.2f

/** 같은 테두리 선에 닿은 것으로 볼 깊이 차이 — 변 길이의 이만큼 (기울어진 스캔) */
private const val LINE_DEPTH_TOLERANCE = 0.005f

/** 좌우 변은 줄 거의 전부가 여백인 곳까지만 (먼지·얼룩 줄은 봐준다) */
private const val STRICT_COVER_RATIO = 0.99f

/** 이만큼 밝으면 종이 테두리 */
private const val MIN_LIGHT_BORDER = 180

/** 이만큼 어두우면 검은 테두리 */
private const val MAX_DARK_BORDER = 85

/** 흰 종이 쪽에서도 쪽 끝에서 이만큼 안의 검은 줄은 스캔 선으로 보고 뺀다 */
private const val THIN_EDGE_RATIO = 0.01f

/** 한 변에서 쪽의 이만큼까지만 뺀다 */
private const val MAX_CROP_RATIO = 0.2f

/** 한 변의 테두리가 쪽의 이만큼보다 얇으면 테두리가 아니다 */
private const val MIN_BORDER_RATIO = 0.01f

/** 위아래·좌우 모두 이만큼도 안 빠지면 그대로 둔다 */
private const val MIN_CROP_RATIO = 0.005f

/** 같은 펼침의 다른 쪽이 테두리를 잴 때까지 기다리는 시간 — 두 쪽은 함께 받는다 */
private const val PARTNER_WAIT_MS = 3000L
private const val PARTNER_POLL_MS = 30L

/** 기억해 둘 쪽 수 — 넘치면 비운다 */
private const val MAX_REMEMBERED = 400

/**
 * 페이지 리더(Coil)용 — 작품 보기 설정 "테두리 잘라내기". 그림 크기가 줄어든다 (화면 맞춤으로 그만큼 크게 보인다).
 *
 * @param self 이 쪽 주소, [partner] 같은 펼침에 함께 보이는 쪽 주소 (한 쪽이면 null)
 * @param left 이 쪽이 화면 왼쪽인지 (바깥 변 = 왼쪽)
 * @param variant 앞선 처리(칸 간격 등) 설정 — 같은 설정으로 잰 다른 쪽의 테두리만 맞춘다
 */
internal class CropBorderTransformation(
    private val mode: CropBorders,
    private val self: String,
    private val partner: String?,
    private val left: Boolean,
    private val variant: String,
) : Transformation() {
    override val cacheKey: String = "crop-borders-5-$mode" + if (partner != null) "-${partner.hashCode()}-${if (left) "L" else "R"}" else ""

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val borders = pageWorkLock.withLock {
            val pixels = IntArray(w * h)
            input.getPixels(pixels, 0, w, 0, 0, w, h)
            findBorders(pixels, w, h, topBottom = mode != CropBorders.LEFT_RIGHT, leftRight = mode != CropBorders.TOP_BOTTOM)
        } ?: IntArray(4)
        // [위, 아래, 바깥, 안쪽] 비율
        val outer = if (left) borders[2] else borders[3]
        val inner = if (left) borders[3] else borders[2]
        val own = floatArrayOf(borders[0] / h.toFloat(), borders[1] / h.toFloat(), outer / w.toFloat(), inner / w.toFloat())
        if (spreadBorders.size > MAX_REMEMBERED) spreadBorders.clear()
        spreadBorders["$self|$mode|$variant"] = own
        val other = partner?.let { awaitBorders("$it|$mode|$variant") }
        val crop = matchCrop(own, other)
        val top = (crop[0] * h).toInt()
        val bottom = (crop[1] * h).toInt()
        val cutLeft = ((if (left) crop[2] else crop[3]) * w).toInt()
        val cutRight = ((if (left) crop[3] else crop[2]) * w).toInt()
        if (top + bottom == 0 && cutLeft + cutRight == 0) return input
        return Bitmap.createBitmap(input, cutLeft, top, w - cutLeft - cutRight, h - top - bottom)
    }

    /** 같은 펼침의 다른 쪽이 잰 테두리 — 그 쪽 처리가 끝날 때까지 잠깐 기다린다. 끝내 없으면 null (이 쪽만 맞춘다) */
    private suspend fun awaitBorders(key: String): FloatArray? {
        var waited = 0L
        while (waited < PARTNER_WAIT_MS) {
            spreadBorders[key]?.let { return it }
            delay(PARTNER_POLL_MS)
            waited += PARTNER_POLL_MS
        }
        return spreadBorders[key]
    }
}
