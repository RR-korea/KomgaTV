package com.komgatv.core.datastore

import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 테스트용 FakeReaderPreferences 구현체
 */
class FakeReaderPreferences(
    initialSettings: ReaderSettings = ReaderSettings(),
) : ReaderPreferences {

    private val _settings = MutableStateFlow(initialSettings)
    override val readerSettings: Flow<ReaderSettings> = _settings.asStateFlow()

    override val pageLayout: Flow<PageLayout> = _settings.map { it.pageLayout }
    override val scaleType: Flow<ScaleType> = _settings.map { it.scaleType }
    override val stretchToFit: Flow<Boolean> = _settings.map { it.stretchToFit }
    override val readingDirection: Flow<ReadingDirection> = _settings.map { it.readingDirection }

    override suspend fun savePageLayout(layout: PageLayout) {
        _settings.value = _settings.value.copy(pageLayout = layout)
    }

    override suspend fun saveScaleType(scaleType: ScaleType) {
        _settings.value = _settings.value.copy(scaleType = scaleType)
    }

    override suspend fun saveStretchToFit(stretch: Boolean) {
        _settings.value = _settings.value.copy(stretchToFit = stretch)
    }

    override suspend fun saveReadingDirection(direction: ReadingDirection) {
        _settings.value = _settings.value.copy(readingDirection = direction)
    }

    override suspend fun clearAll() {
        _settings.value = ReaderSettings()
    }
}

/**
 * 리더 보기 설정 저장 및 읽기 단위 테스트 (가짜 저장소 검증)
 */
class ReaderPreferencesTest {

    @Test
    fun `기본 설정값 확인 - Komelia 기본값과 동일해야 한다`() = runTest {
        val prefs = FakeReaderPreferences()

        val settings = prefs.readerSettings.first()
        assertEquals(PageLayout.SINGLE_PAGE, settings.pageLayout)
        assertEquals(ScaleType.SCREEN, settings.scaleType)
        assertTrue(settings.stretchToFit)
        assertEquals(ReadingDirection.LEFT_TO_RIGHT, settings.readingDirection)
    }

    @Test
    fun `설정 저장 및 읽기 - 변경된 값들이 정상적으로 반영된다`() = runTest {
        val prefs = FakeReaderPreferences()

        prefs.savePageLayout(PageLayout.DOUBLE_PAGES)
        assertEquals(PageLayout.DOUBLE_PAGES, prefs.pageLayout.first())
        assertEquals(PageLayout.DOUBLE_PAGES, prefs.readerSettings.first().pageLayout)

        prefs.saveScaleType(ScaleType.FIT_WIDTH)
        assertEquals(ScaleType.FIT_WIDTH, prefs.scaleType.first())
        assertEquals(ScaleType.FIT_WIDTH, prefs.readerSettings.first().scaleType)

        prefs.saveStretchToFit(false)
        assertFalse(prefs.stretchToFit.first())
        assertFalse(prefs.readerSettings.first().stretchToFit)

        prefs.saveReadingDirection(ReadingDirection.RIGHT_TO_LEFT)
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, prefs.readingDirection.first())
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, prefs.readerSettings.first().readingDirection)
    }

    @Test
    fun `설정 초기화 - clearAll 호출 시 기본값으로 복원된다`() = runTest {
        val prefs = FakeReaderPreferences()

        prefs.savePageLayout(PageLayout.DOUBLE_PAGES_NO_COVER)
        prefs.saveScaleType(ScaleType.ORIGINAL)
        prefs.saveStretchToFit(false)
        prefs.saveReadingDirection(ReadingDirection.RIGHT_TO_LEFT)

        prefs.clearAll()

        val reset = prefs.readerSettings.first()
        assertEquals(PageLayout.SINGLE_PAGE, reset.pageLayout)
        assertEquals(ScaleType.SCREEN, reset.scaleType)
        assertTrue(reset.stretchToFit)
        assertEquals(ReadingDirection.LEFT_TO_RIGHT, reset.readingDirection)
    }
}
