package com.komgatv.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.komgatv.core.model.PageLayout
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.model.ReaderType
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
 * 리더 전역 보기 설정 (모든 책에 쓰는 기본값)
 */
interface ReaderPreferences {
    val readerSettings: Flow<ReaderSettings>

    suspend fun saveReaderType(readerType: ReaderType)
    suspend fun savePageLayout(layout: PageLayout)
    suspend fun saveScaleType(scaleType: ScaleType)
    suspend fun saveStretchToFit(stretch: Boolean)
    suspend fun saveReadingDirection(direction: ReadingDirection)
    suspend fun saveContinuousPadding(padding: Float)
    suspend fun saveContinuousPageSpacing(spacing: Int)
}

/**
 * DataStore 기반 구현. 저장된 값이 없거나 알 수 없는 값이면 [ReaderSettings]의 기본값(Komelia와 같음)을 쓴다.
 */
@Singleton
class DefaultReaderPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReaderPreferences {

    private companion object {
        val KEY_READER_TYPE = stringPreferencesKey("reader_type")
        val KEY_PAGE_LAYOUT = stringPreferencesKey("page_layout")
        val KEY_SCALE_TYPE = stringPreferencesKey("scale_type")
        val KEY_STRETCH_TO_FIT = booleanPreferencesKey("stretch_to_fit")
        val KEY_READING_DIRECTION = stringPreferencesKey("reading_direction")
        val KEY_CONTINUOUS_PADDING = floatPreferencesKey("continuous_padding")
        val KEY_CONTINUOUS_PAGE_SPACING = intPreferencesKey("continuous_page_spacing")
        val DEFAULTS = ReaderSettings()
    }

    override val readerSettings: Flow<ReaderSettings> = context.readerDataStore.data.map { prefs ->
        ReaderSettings(
            readerType = enumOrNull<ReaderType>(prefs[KEY_READER_TYPE]) ?: DEFAULTS.readerType,
            pageLayout = enumOrNull<PageLayout>(prefs[KEY_PAGE_LAYOUT]) ?: DEFAULTS.pageLayout,
            scaleType = enumOrNull<ScaleType>(prefs[KEY_SCALE_TYPE]) ?: DEFAULTS.scaleType,
            stretchToFit = prefs[KEY_STRETCH_TO_FIT] ?: DEFAULTS.stretchToFit,
            readingDirection = enumOrNull<ReadingDirection>(prefs[KEY_READING_DIRECTION])
                ?: DEFAULTS.readingDirection,
            continuousPadding = prefs[KEY_CONTINUOUS_PADDING] ?: DEFAULTS.continuousPadding,
            continuousPageSpacing = prefs[KEY_CONTINUOUS_PAGE_SPACING] ?: DEFAULTS.continuousPageSpacing,
        )
    }

    override suspend fun saveReaderType(readerType: ReaderType) {
        context.readerDataStore.edit { it[KEY_READER_TYPE] = readerType.name }
    }

    override suspend fun savePageLayout(layout: PageLayout) {
        context.readerDataStore.edit { it[KEY_PAGE_LAYOUT] = layout.name }
    }

    override suspend fun saveScaleType(scaleType: ScaleType) {
        context.readerDataStore.edit { it[KEY_SCALE_TYPE] = scaleType.name }
    }

    override suspend fun saveStretchToFit(stretch: Boolean) {
        context.readerDataStore.edit { it[KEY_STRETCH_TO_FIT] = stretch }
    }

    override suspend fun saveReadingDirection(direction: ReadingDirection) {
        context.readerDataStore.edit { it[KEY_READING_DIRECTION] = direction.name }
    }

    override suspend fun saveContinuousPadding(padding: Float) {
        context.readerDataStore.edit { it[KEY_CONTINUOUS_PADDING] = padding }
    }

    override suspend fun saveContinuousPageSpacing(spacing: Int) {
        context.readerDataStore.edit { it[KEY_CONTINUOUS_PAGE_SPACING] = spacing }
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
