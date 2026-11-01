package com.komgatv.feature.reader.translate

/**
 * 쪽 번역 결과 ↔ 문자열. 첫 줄은 판 번호, 다음부터 한 덩어리에 한 줄: "left,top,right,bottom<TAB>번역문".
 * 번역문의 \, 줄바꿈, 탭은 이스케이프한다. 판이 다르거나 깨진 줄은 버린다.
 */
internal object TranslationCacheCodec {
    private const val VERSION = "v1"

    fun encode(blocks: List<TranslatedBlock>): String = buildString {
        append(VERSION).append('\n')
        for (b in blocks) {
            append(b.box.left).append(',').append(b.box.top).append(',')
            append(b.box.right).append(',').append(b.box.bottom).append('\t')
            append(escape(b.text)).append('\n')
        }
    }

    /** 판이 다르면 null — 다시 번역한다 */
    fun decode(text: String): List<TranslatedBlock>? {
        val lines = text.split('\n')
        if (lines.firstOrNull() != VERSION) return null
        return lines.drop(1).mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val n = line.substring(0, tab).split(',').mapNotNull { it.toFloatOrNull() }
            if (n.size != 4) return@mapNotNull null
            TranslatedBlock(Box(n[0], n[1], n[2], n[3]), unescape(line.substring(tab + 1)))
        }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")

    private fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                out.append(
                    when (s[i + 1]) {
                        'n' -> '\n'
                        't' -> '\t'
                        else -> s[i + 1]
                    },
                )
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
