package com.komgatv.core.datastore

import com.komgatv.core.model.ImageEnhancement
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.PageTransition
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.model.ReaderType
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType

/**
 * 작품 설정 ↔ 문자열 ("키=값;키=값"). 모르는 키·잘못된 값은 기본값으로 둔다 (앱이 바뀌어도 읽힌다).
 */
internal object ReaderSettingsCodec {

    fun encode(s: ReaderSettings): String = listOf(
        "readerType" to s.readerType.name,
        "pageLayout" to s.pageLayout.name,
        "scaleType" to s.scaleType.name,
        "readingDirection" to s.readingDirection.name,
        "continuousPadding" to s.continuousPadding.toString(),
        "continuousPageSpacing" to s.continuousPageSpacing.toString(),
        "imageEnhancement" to s.imageEnhancement.name,
        "zoomPercent" to s.zoomPercent.toString(),
        "contrastPercent" to s.contrastPercent.toString(),
        "brightnessPercent" to s.brightnessPercent.toString(),
        "pageTransition" to s.pageTransition.name,
    ).joinToString(";") { (k, v) -> "$k=$v" }

    fun decode(text: String?): ReaderSettings? {
        if (text.isNullOrBlank()) return null
        val map = text.split(";").mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
        }.toMap()
        val d = ReaderSettings()
        return ReaderSettings(
            readerType = enumOrNull<ReaderType>(map["readerType"]) ?: d.readerType,
            pageLayout = enumOrNull<PageLayout>(map["pageLayout"]) ?: d.pageLayout,
            scaleType = enumOrNull<ScaleType>(map["scaleType"]) ?: d.scaleType,
            readingDirection = enumOrNull<ReadingDirection>(map["readingDirection"]) ?: d.readingDirection,
            continuousPadding = map["continuousPadding"]?.toFloatOrNull() ?: d.continuousPadding,
            continuousPageSpacing = map["continuousPageSpacing"]?.toIntOrNull() ?: d.continuousPageSpacing,
            imageEnhancement = enumOrNull<ImageEnhancement>(map["imageEnhancement"]) ?: d.imageEnhancement,
            zoomPercent = map["zoomPercent"]?.toIntOrNull()
                ?.coerceIn(ReaderSettings.MIN_ZOOM_PERCENT, ReaderSettings.MAX_ZOOM_PERCENT) ?: d.zoomPercent,
            pageTransition = enumOrNull<PageTransition>(map["pageTransition"]) ?: d.pageTransition,
            contrastPercent = map["contrastPercent"]?.toIntOrNull()
                ?.coerceIn(ReaderSettings.MIN_CONTRAST_PERCENT, ReaderSettings.MAX_CONTRAST_PERCENT) ?: d.contrastPercent,
            brightnessPercent = map["brightnessPercent"]?.toIntOrNull()
                ?.coerceIn(ReaderSettings.MIN_BRIGHTNESS_PERCENT, ReaderSettings.MAX_BRIGHTNESS_PERCENT) ?: d.brightnessPercent,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
}
