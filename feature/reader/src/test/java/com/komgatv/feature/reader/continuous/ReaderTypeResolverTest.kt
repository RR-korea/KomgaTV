package com.komgatv.feature.reader.continuous

import com.komgatv.core.model.ReaderType
import com.komgatv.core.model.ReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTypeResolverTest {

    @Test
    fun resolve_ltr_or_rtl_returns_paged() {
        // 시리즈 방향이 LTR 또는 RTL이면 저장된 설정이 CONTINUOUS여도 항상 PAGED
        val ltrResult = ReaderTypeResolver.resolveReaderType(
            seriesReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
            savedReaderType = ReaderType.CONTINUOUS,
        )
        assertEquals(ReaderType.PAGED, ltrResult)

        val rtlResult = ReaderTypeResolver.resolveReaderType(
            seriesReadingDirection = ReadingDirection.RIGHT_TO_LEFT,
            savedReaderType = ReaderType.CONTINUOUS,
        )
        assertEquals(ReaderType.PAGED, rtlResult)
    }

    @Test
    fun resolve_webtoon_returns_continuous() {
        // 시리즈 방향이 WEBTOON이면 저장된 설정이 PAGED여도 항상 CONTINUOUS
        val result = ReaderTypeResolver.resolveReaderType(
            seriesReadingDirection = ReadingDirection.WEBTOON,
            savedReaderType = ReaderType.PAGED,
        )
        assertEquals(ReaderType.CONTINUOUS, result)
    }

    @Test
    fun resolve_vertical_returns_saved_preference() {
        // 시리즈 방향이 VERTICAL이면 저장된 설정(PAGED 또는 CONTINUOUS)을 따름
        val pagedResult = ReaderTypeResolver.resolveReaderType(
            seriesReadingDirection = ReadingDirection.VERTICAL,
            savedReaderType = ReaderType.PAGED,
        )
        assertEquals(ReaderType.PAGED, pagedResult)

        val continuousResult = ReaderTypeResolver.resolveReaderType(
            seriesReadingDirection = ReadingDirection.VERTICAL,
            savedReaderType = ReaderType.CONTINUOUS,
        )
        assertEquals(ReaderType.CONTINUOUS, continuousResult)
    }

    @Test
    fun resolve_null_direction_returns_saved_preference() {
        // 시리즈 방향 메타데이터가 없으면(null) 저장된 설정을 따름
        val pagedResult = ReaderTypeResolver.resolveReaderType(
            seriesReadingDirection = null,
            savedReaderType = ReaderType.PAGED,
        )
        assertEquals(ReaderType.PAGED, pagedResult)

        val continuousResult = ReaderTypeResolver.resolveReaderType(
            seriesReadingDirection = null,
            savedReaderType = ReaderType.CONTINUOUS,
        )
        assertEquals(ReaderType.CONTINUOUS, continuousResult)
    }
}
