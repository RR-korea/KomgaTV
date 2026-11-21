package com.komgatv.feature.reader.enhance

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** 조사용 — 목록 파일의 쪽마다 상하좌우 테두리 잘라내기 결과(원본+선 | 잘라낸 것)를 JPG로 쓴다. CROP_LIST·CROP_OUT 이 없으면 건너뛴다 (tools/reader-survey/README.md) */
class CropProbeTest {
    @Test
    fun probe() {
        val list = System.getenv("CROP_LIST")
        val out = System.getenv("CROP_OUT")
        assumeTrue(list != null && out != null)
        // CROP_BLANK_ONLY 가 있으면 칸 간격 없애기 뒤의 두 번째 자르기(모든 열이 빈 줄만)
        val blankOnly = System.getenv("CROP_BLANK_ONLY") != null
        File(out!!).mkdirs()
        File(list!!).readLines().filter { it.isNotBlank() }.forEachIndexed { n, path ->
            val img = ImageIO.read(File(path)) ?: return@forEachIndexed
            val w = img.width
            val h = img.height
            val px = IntArray(w * h)
            img.getRGB(0, 0, w, h, px, 0, w)
            val b = findBorders(px, w, h, topBottom = true, leftRight = true, blankOnly = blankOnly) ?: IntArray(4)
            println("CROP $n ${b.joinToString(",")} $path")
            val scale = 900.0 / h
            val sw = (w * scale).toInt()
            val canvas = BufferedImage(sw * 2 + 10, 900, BufferedImage.TYPE_INT_RGB)
            val g = canvas.createGraphics()
            g.color = Color.RED
            g.fillRect(0, 0, canvas.width, 900)
            g.drawImage(img, 0, 0, sw, 900, null)
            g.stroke = BasicStroke(2f)
            g.drawLine(0, (b[0] * scale).toInt(), sw, (b[0] * scale).toInt())
            g.drawLine(0, ((h - b[1]) * scale).toInt(), sw, ((h - b[1]) * scale).toInt())
            g.drawLine((b[2] * scale).toInt(), 0, (b[2] * scale).toInt(), 900)
            g.drawLine(((w - b[3]) * scale).toInt(), 0, ((w - b[3]) * scale).toInt(), 900)
            val cw = w - b[2] - b[3]
            val ch = h - b[0] - b[1]
            val cs = minOf(900.0 / ch, sw.toDouble() / cw)
            g.drawImage(img.getSubimage(b[2], b[0], cw, ch), sw + 10, 0, (cw * cs).toInt(), (ch * cs).toInt(), null)
            g.dispose()
            ImageIO.write(canvas, "jpg", File(out, "%03d.jpg".format(n)))
        }
    }
}
