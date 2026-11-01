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
            // 인식 상자는 글자에 딱 붙거나 첫·끝 글자를 조금 놓친다 — 줄 높이의 4분의 1쯤 넓게 가린다
            val pad = max(4f, min(original.width(), original.height()) * 0.25f)
            original.inset(-pad, -pad)
            val area = textArea(original, w, h)
            val background = sampleBackground(out, original)

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
     * 번역문을 쓸 칸. 일본어 세로 말풍선은 가늘고 길어 한국어 가로글이 한 글자씩 꺾인다 — 너비를 조금 넓힌다(쪽 밖으로는 안 나가게).
     */
    private fun textArea(original: RectF, pageW: Float, pageH: Float): RectF {
        val area = RectF(original)
        val wanted = min(original.height() * 0.6f, original.width() * 1.6f)
        if (wanted > area.width()) {
            val grow = (wanted - area.width()) / 2f
            area.left -= grow
            area.right += grow
        }
        area.left = area.left.coerceAtLeast(0f)
        area.top = area.top.coerceAtLeast(0f)
        area.right = area.right.coerceAtMost(pageW)
        area.bottom = area.bottom.coerceAtMost(pageH)
        return area
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
