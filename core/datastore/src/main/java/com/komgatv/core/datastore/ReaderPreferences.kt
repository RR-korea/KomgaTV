package com.komgatv.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.model.ReadingDirection
import com.komgatv.core.model.ScaleType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_preferences"
)

/**
 * 리더 보기 설정 인터페이스
 */
interface ReaderPreferences {
    val readerSettings: Flow<ReaderSettings>
    val pageLayout: Flow<PageLayout>
    val scaleType: Flow<ScaleType>
    val stretchToFit: Flow<Boolean>
    val readingDirection: Flow<ReadingDirection>

    suspend fun savePageLayout(layout: PageLayout)
    suspend fun saveScaleType(scaleType: ScaleType)
    suspend fun saveStretchToFit(stretch: Boolean)
    suspend fun saveReadingDirection(direction: ReadingDirection)
    suspend fun clearAll()
}

/**
 * DataStore 기반 리더 보기 설정 구현체
 *
 * Komelia의 ImageReaderSettings 기본값을 따릅니다:
 * - pageLayout: SINGLE_PAGE
 * - scaleType: SCREEN
 * - stretchToFit: true
 * - readingDirection: LEFT_TO_RIGHT
 */
@Singleton
class DefaultReaderPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReaderPreferences {

    companion object {
        private val KEY_PAGE_LAYOUT = stringPreferencesKey("page_layout")
        private val KEY_SCALE_TYPE = stringPreferencesKey("scale_type")
        private val KEY_STRETCH_TO_FIT = booleanPreferencesKey("stretch_to_fit")
        private val KEY_READING_DIRECTION = stringPreferencesKey("reading_direction")
    }

    override val readerSettings: Flow<ReaderSettings> = context.readerDataStore.data.map { prefs ->
        val layout = prefs[KEY_PAGE_LAYOUT]?.let { runCatching { PageLayout.valueOf(it) }.getOrNull() }
            ?: PageLayout.SINGLE_PAGE
        val scale = prefs[KEY_SCALE_TYPE]?.let { runCatching { ScaleType.valueOf(it) }.getOrNull() }
            ?: ScaleType.SCREEN
        val stretch = prefs[KEY_STRETCH_TO_FIT] ?: true
        val direction = prefs[KEY_READING_DIRECTION]?.let { runCatching { ReadingDirection.valueOf(it) }.getOrNull() }
            ?: ReadingDirection.LEFT_TO_RIGHT

        ReaderSettings(
            pageLayout = layout,
            scaleType = scale,
            stretchToFit = stretch,
            readingDirection = direction,
        )
    }

    override val pageLayout: Flow<PageLayout> = context.readerDataStore.data.map { prefs ->
        prefs[KEY_PAGE_LAYOUT]?.let { runCatching { PageLayout.valueOf(it) }.getOrNull() }
            ?: PageLayout.SINGLE_PAGE
    }

    override val scaleType: Flow<ScaleType> = context.readerDataStore.data.map { prefs ->
        prefs[KEY_SCALE_TYPE]?.let { runCatching { ScaleType.valueOf(it) }.getOrNull() }
            ?: ScaleType.SCREEN
    }

    override val stretchToFit: Flow<Boolean> = context.readerDataStore.data.map { prefs ->
        prefs[KEY_STRETCH_TO_FIT] ?: true
    }

    override val readingDirection: Flow<ReadingDirection> = context.readerDataStore.data.map { prefs ->
        prefs[KEY_READING_DIRECTION]?.let { runCatching { ReadingDirection.valueOf(it) }.getOrNull() }
            ?: ReadingDirection.LEFT_TO_RIGHT
    }

    override suspend fun savePageLayout(layout: PageLayout) {
        context.readerDataStore.edit { prefs ->
            prefs[KEY_PAGE_LAYOUT] = layout.name
        }
    }

    override suspend fun saveScaleType(scaleType: ScaleType) {
        context.readerDataStore.edit { prefs ->
            prefs[KEY_SCALE_TYPE] = scaleType.name
        }
    }

    override suspend fun saveStretchToFit(stretch: Boolean) {
        context.readerDataStore.edit { prefs ->
            prefs[KEY_STRETCH_TO_FIT] = stretch
        }
    }

    override suspend fun saveReadingDirection(direction: ReadingDirection) {
        context.readerDataStore.edit { prefs ->
            prefs[KEY_READING_DIRECTION] = direction.name
        }
    }

    override suspend fun clearAll() {
        context.readerDataStore.edit { it.clear() }
    }
}
