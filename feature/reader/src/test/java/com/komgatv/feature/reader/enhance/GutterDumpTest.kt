package com.komgatv.feature.reader.enhance

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** 조사용 — 폴더 아래 모든 쪽에 collapseGutters 를 돌려 결과를 PNG로 쓴다 (원본 크기). GUT_DUMP_SRC·GUT_DUMP_OUT 이 없으면 건너뛴다 (tools/reader-survey/README.md) */
class GutterDumpTest {
    @Test
    fun dump() {
        val src = System.getenv("GUT_DUMP_SRC")
        val out = System.getenv("GUT_DUMP_OUT")
        assumeTrue(src != null && out != null)
        val root = File(src!!)
        root.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }.sorted().forEach { f ->
            val rel = f.relativeTo(root).path.substringBeforeLast('.')
            val dst = File(out!!, "$rel.png")
            if (dst.exists()) return@forEach
            val img = ImageIO.read(f) ?: return@forEach
            val w = img.width
            val h = img.height
            val px = IntArray(w * h)
            img.getRGB(0, 0, w, h, px, 0, w)
            val res = collapseGutters(px, w, h) ?: px
            val o = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            o.setRGB(0, 0, w, h, res, 0, w)
            dst.parentFile.mkdirs()
            ImageIO.write(o, "png", dst)
        }
    }
}
