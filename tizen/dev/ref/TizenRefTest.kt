package com.komgatv.feature.reader.enhance

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Tizen 이식 확인용 — raw 쪽마다 Kotlin 결과를 한 줄로 쓴다 (tizen/docs/PLAN.md "Kotlin 대조").
 * Android 저장소에는 넣지 않는다: main 을 별도 worktree 로 꺼내 이 파일을
 * feature/reader/src/test/java/com/komgatv/feature/reader/enhance/ 에 복사하고 (local.properties 도 복사)
 *   TIZEN_REF_IN=<raw 폴더> TIZEN_REF_OUT=<결과 표 .tsv> ./gradlew :feature:reader:testDebugUnitTest --tests '*TizenRefTest*' --rerun -q
 */
class TizenRefTest {
    private fun crc(px: IntArray): Long {
        val buf = ByteBuffer.allocate(px.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.asIntBuffer().put(px)
        return CRC32().apply { update(buf.array()) }.value
    }

    private fun crop(px: IntArray, w: Int, h: Int, b: IntArray): Triple<IntArray, Int, Int> {
        val nw = w - b[2] - b[3]
        val nh = h - b[0] - b[1]
        val out = IntArray(nw * nh)
        for (y in 0 until nh) System.arraycopy(px, (y + b[0]) * w + b[2], out, y * nw, nw)
        return Triple(out, nw, nh)
    }

    @Test
    fun dump() {
        val src = System.getenv("TIZEN_REF_IN")
        val out = System.getenv("TIZEN_REF_OUT")
        assumeTrue(src != null && out != null)
        val lines = ArrayList<String>()
        File(src!!).listFiles { f -> f.extension == "bin" }!!.sortedBy { it.name }.forEach { f ->
            val buf = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val w = buf.int
            val h = buf.int
            val px = IntArray(w * h)
            buf.asIntBuffer().get(px)
            fun b(tb: Boolean, lr: Boolean, blank: Boolean = false) =
                findBorders(px, w, h, tb, lr, blank)?.joinToString(",") ?: "null"
            val gut = collapseGutters(px, w, h)
            // 앱 순서: 여백 자르기(네 변) → 칸 간격 → 빈 줄만 한 번 더
            var cur = Triple(px, w, h)
            findBorders(cur.first, cur.second, cur.third, true, true)?.let { cur = crop(cur.first, cur.second, cur.third, it) }
            collapseGutters(cur.first, cur.second, cur.third)?.let { cur = Triple(it, cur.second, cur.third) }
            findBorders(cur.first, cur.second, cur.third, true, true, true)?.let { cur = crop(cur.first, cur.second, cur.third, it) }
            lines += listOf(
                f.nameWithoutExtension, w, h, b(true, true), b(true, false), b(false, true), b(true, true, true),
                gut?.let { crc(it) } ?: "null", cur.second, cur.third, crc(cur.first),
            ).joinToString("\t")
        }
        File(out!!).writeText(lines.joinToString("\n") + "\n")
    }
}
