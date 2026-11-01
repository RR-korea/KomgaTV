package com.komgatv.feature.reader.translate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.get
import androidx.core.graphics.withTranslation
import kotlin.math.max
import kotlin.math.min

/**
 * 번역한 덩어리를 쪽 그림에 그린다 — 원래 글자 자리를 바탕색으로 가리고, 그 칸에 맞는 크기로 번역문을 쓴다.
 */
internal object TranslatePainter {

    private const val MIN_TEXT_PX = 9f

    fun paint(page: Bitmap, blocks: List<TranslatedBlock>): Bitmap {
        if (blocks.isEmpty()) return page
        val out = page.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        for (block in blocks) {
            val original = RectF(block.box.left * w, block.box.top * h, block.box.right * w, block.box.bottom * h)
            // 인식 상자는 글자에 딱 붙어 있다 — 조금 넓히고, 바탕색인 곳으로만 더 넓혀 가린다(말풍선 테두리는 남긴다)
            val pad = max(3f, min(original.width(), original.height()) * 0.08f)
            original.inset(-pad, -pad)
            original.intersect(0f, 0f, w, h)
            val background = sampleBackground(out, original)
            growIntoBackground(out, original, background, min(original.width(), original.height()) * 0.25f)
            val area = textArea(out, original, background)

            fill.color = background
            val radius = min(area.width(), area.height()) * 0.12f
            canvas.drawRoundRect(area, radius, radius, fill)

            textPaint.color = if (luminance(background) > 140) Color.BLACK else Color.WHITE
            drawFitted(canvas, block.text, area, textPaint, pad)
        }
        if (page.config == Bitmap.Config.RGB_565) {
            return out.copy(Bitmap.Config.RGB_565, false).also { out.recycle() }
        }
        return out
    }

    /**
     * 번역문을 쓸 칸. 일본어 세로 말풍선은 가늘고 길어 한국어 가로글이 한 글자씩 꺾인다 — 너비를 넓힌다.
     * 바탕색인 곳(말풍선 안)까지만 넓혀 말풍선 테두리나 그림을 덮지 않는다.
     */
    private fun textArea(bitmap: Bitmap, original: RectF, background: Int): RectF {
        val area = RectF(original)
        val wanted = min(original.height() * 0.6f, original.width() * 1.6f)
        val step = 2f
        var growLeft = true
        var growRight = true
        while (area.width() < wanted && (growLeft || growRight)) {
            if (growLeft) growLeft = area.left - step >= 0 && isBackgroundColumn(bitmap, area.left - step, area, background)
            if (growLeft) area.left -= step
            if (growRight) growRight = area.right + step < bitmap.width && isBackgroundColumn(bitmap, area.right + step, area, background)
            if (growRight) area.right += step
        }
        return area
    }

    /** 네 변을 바탕색인 곳으로만 [limit]까지 넓힌다 */
    private fun growIntoBackground(bitmap: Bitmap, rect: RectF, background: Int, limit: Float) {
        val step = 2f
        var grown = 0f
        while (grown < limit) {
            var any = false
            if (rect.left - step >= 0 && isBackgroundColumn(bitmap, rect.left - step, rect, background)) {
                rect.left -= step
                any = true
            }
            if (rect.right + step < bitmap.width && isBackgroundColumn(bitmap, rect.right + step, rect, background)) {
                rect.right += step
                any = true
            }
            if (rect.top - step >= 0 && isBackgroundRow(bitmap, rect.top - step, rect, background)) {
                rect.top -= step
                any = true
            }
            if (rect.bottom + step < bitmap.height && isBackgroundRow(bitmap, rect.bottom + step, rect, background)) {
                rect.bottom += step
                any = true
            }
            if (!any) break
            grown += step
        }
    }

    /** 이 가로줄이 거의 바탕색인지 */
    private fun isBackgroundRow(bitmap: Bitmap, y: Float, area: RectF, background: Int): Boolean {
        val row = y.toInt().coerceIn(0, bitmap.height - 1)
        val left = area.left.toInt().coerceIn(0, bitmap.width - 1)
        val right = area.right.toInt().coerceIn(0, bitmap.width - 1)
        val target = luminance(background)
        var total = 0
        var off = 0
        for (x in left..right step 2) {
            total++
            if (kotlin.math.abs(luminance(bitmap[x, row]) - target) > 40) off++
        }
        return total == 0 || off * 50 <= total
    }

    /** 이 세로줄이 거의 바탕색인지 (글자·테두리·그림이 없다) */
    private fun isBackgroundColumn(bitmap: Bitmap, x: Float, area: RectF, background: Int): Boolean {
        val col = x.toInt().coerceIn(0, bitmap.width - 1)
        val top = area.top.toInt().coerceIn(0, bitmap.height - 1)
        val bottom = area.bottom.toInt().coerceIn(0, bitmap.height - 1)
        val target = luminance(background)
        var total = 0
        var off = 0
        for (y in top..bottom step 2) {
            total++
            if (kotlin.math.abs(luminance(bitmap[col, y]) - target) > 40) off++
        }
        return total == 0 || off * 50 <= total
    }

    /** 칸 테두리 픽셀의 중앙값 색 — 말풍선이면 흰색, 그림 위 글자면 주변 색 */
    private fun sampleBackground(bitmap: Bitmap, rect: RectF): Int {
        val l = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val t = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val r = rect.right.toInt().coerceIn(0, bitmap.width - 1)
        val b = rect.bottom.toInt().coerceIn(0, bitmap.height - 1)
        val samples = ArrayList<Int>()
        val step = max(1, (r - l + b - t) / 80)
        for (x in l..r step step) {
            samples += bitmap[x, t]
            samples += bitmap[x, b]
        }
        for (y in t..b step step) {
            samples += bitmap[l, y]
            samples += bitmap[r, y]
        }
        if (samples.isEmpty()) return Color.WHITE
        return samples.sortedBy { luminance(it) }[samples.size / 2]
    }

    private fun luminance(color: Int): Int =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000

    /** 칸에 들어가는 가장 큰 글자 크기를 찾아 가운데에 쓴다 */
    private fun drawFitted(canvas: Canvas, text: String, area: RectF, paint: TextPaint, pad: Float) {
        val width = (area.width() - pad).toInt().coerceAtLeast(1)
        val height = area.height() - pad
        var low = MIN_TEXT_PX
        var high = max(MIN_TEXT_PX, min(area.height(), area.width()) * 0.45f)
        var best = layout(text, paint, low, width)
        repeat(12) {
            val mid = (low + high) / 2f
            val candidate = layout(text, paint, mid, width)
            if (candidate.height <= height) {
                best = candidate
                low = mid
            } else {
                high = mid
            }
        }
        val dx = area.left + (area.width() - width) / 2f
        val dy = area.top + (area.height() - best.height) / 2f
        canvas.withTranslation(dx, dy) { best.draw(this) }
    }

    private fun layout(text: String, paint: TextPaint, size: Float, width: Int): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(false)
            .build()
    }
}
