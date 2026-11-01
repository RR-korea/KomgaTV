package com.komgatv.feature.reader.translate

import com.komgatv.core.model.TranslationMode

/** 쪽 안의 사각형 — 0..1 비율(쪽 너비·높이 기준) */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f

    fun expand(by: Float) = Box(left - by, top - by, right + by, bottom + by)

    fun intersects(other: Box) =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun union(other: Box) = Box(
        minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom),
    )
}

/** 글자 인식 결과 한 줄 (세로쓰기면 한 열) */
data class OcrLine(val box: Box, val text: String)

/** 글자 인식 결과 한 덩어리 — 보통 말풍선 하나 */
data class OcrBlock(val box: Box, val lines: List<OcrLine>)

/** 번역해 그릴 한 덩어리: 원문 자리와 번역문 */
data class TranslatedBlock(val box: Box, val text: String)

/** 줄 대부분이 세로로 길면 세로쓰기 (일본 만화 말풍선) */
internal fun OcrBlock.isVertical(): Boolean =
    lines.isNotEmpty() && lines.count { it.box.height > it.box.width * 1.5f } * 2 > lines.size

/**
 * 덩어리의 원문. 세로쓰기는 오른쪽 열부터, 가로쓰기는 위 줄부터 읽는다.
 * 일본어는 줄 사이를 붙이고, 영어는 띄우되 줄 끝 하이픈(단어가 나뉜 곳)은 붙인다.
 */
internal fun OcrBlock.sourceText(mode: TranslationMode): String {
    val ordered = if (isVertical()) lines.sortedByDescending { it.box.centerX } else lines.sortedBy { it.box.top }
    val parts = ordered.map { it.text.trim() }.filter { it.isNotEmpty() }
    if (mode == TranslationMode.JA_KO) return parts.joinToString("")
    val joined = StringBuilder()
    for (part in parts) {
        if (joined.isEmpty()) {
            joined.append(part)
        } else if (joined.endsWith("-") && joined.length > 1 && joined[joined.length - 2].isLetter()) {
            joined.setLength(joined.length - 1)
            joined.append(part)
        } else {
            joined.append(' ').append(part)
        }
    }
    return normalizeEnglish(joined.toString())
}

/**
 * 만화 영어는 거의 대문자라 번역기가 고유명사로 오해한다 — 대부분 대문자면 문장 첫 글자와 "I"만 대문자로.
 */
internal fun normalizeEnglish(text: String): String {
    val letters = text.filter { it.isLetter() }
    if (letters.isEmpty() || letters.count { it.isUpperCase() } * 10 < letters.length * 7) return text
    val lower = text.lowercase()
    val out = StringBuilder(lower.length)
    var capitalizeNext = true
    for ((i, c) in lower.withIndex()) {
        when {
            c.isLetter() && capitalizeNext -> {
                out.append(c.uppercaseChar())
                capitalizeNext = false
            }
            // 혼자 쓰인 i (I, I'm, I'll)
            c == 'i' && (i == 0 || !lower[i - 1].isLetter()) && (i + 1 == lower.length || !lower[i + 1].isLetter()) ->
                out.append('I')
            else -> out.append(c)
        }
        if (c == '.' || c == '!' || c == '?') capitalizeNext = true
    }
    return out.toString()
}

/**
 * 번역할 만한 글인지. 빼는 것:
 * - 글자가 없거나 숫자·기호가 대부분인 것 (쪽 번호, ISBN, 바코드)
 * - 이미 한국어인 것 (한국어판의 원래 글자)
 * - 고른 언어가 아닌 것 — 영어는 로마자가, 일본어는 가나·한자가 있어야 한다 (인식기가 다른 글자를 잘못 읽은 잡음)
 */
internal fun worthTranslating(text: String, mode: TranslationMode): Boolean {
    val visible = text.filterNot { it.isWhitespace() }
    val letters = visible.count { it.isLetter() }
    if (letters == 0 || letters * 2 < visible.length) return false
    // 한글 음절·자모
    if (visible.any { it in '가'..'힣' || it in 'ㄱ'..'ㆎ' }) return false
    return when (mode) {
        TranslationMode.EN_KO -> letters >= 2 && visible.count { it in 'A'..'Z' || it in 'a'..'z' } * 10 >= letters * 9
        // 히라가나·가타카나, 한자
        TranslationMode.JA_KO -> visible.any { it in '぀'..'ヿ' || it in '一'..'鿿' }
        TranslationMode.OFF -> false
    }
}

/**
 * 겹치거나 아주 가까운 덩어리를 합친다 — 인식기가 한 말풍선을 여러 덩어리로 나눌 때가 있다.
 * [gap]은 이만큼 떨어져 있어도 붙어 있다고 본다 (쪽 비율).
 */
internal fun mergeBlocks(blocks: List<OcrBlock>, gap: Float = 0.006f): List<OcrBlock> {
    val merged = blocks.toMutableList()
    var changed = true
    while (changed) {
        changed = false
        loop@ for (i in merged.indices) {
            for (j in i + 1 until merged.size) {
                val a = merged[i]
                val b = merged[j]
                if (a.isVertical() == b.isVertical() && a.box.expand(gap).intersects(b.box.expand(gap))) {
                    merged[i] = OcrBlock(a.box.union(b.box), a.lines + b.lines)
                    merged.removeAt(j)
                    changed = true
                    break@loop
                }
            }
        }
    }
    return merged
}
