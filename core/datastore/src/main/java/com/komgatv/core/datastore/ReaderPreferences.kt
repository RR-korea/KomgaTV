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
import com.komgatv.core.model.ImageEnhancement
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
 * 리더 보기 설정
 *
 * - 앱 전체 기본값: 서버 설정 화면의 "보기 기본값"에서 바꾼다.
 * - 작품(시리즈 = Komga의 한 폴더) 설정: 리더의 보기 설정 패널에서 바꾸면 그 작품에만 저장된다.
 *   설정이 없는 작품은 기본값을 따른다.
 */
interface ReaderPreferences {
    /** 앱 전체 기본값 */
    val readerSettings: Flow<ReaderSettings>

    suspend fun saveReaderSettings(settings: ReaderSettings)

    /** EPUB 글자 크기 (앱 전체) */
    suspend fun saveEpubTextZoom(zoom: Int)

    /** 작품 설정. 없으면 null — 기본값을 따른다 */
    fun seriesSettings(seriesId: String): Flow<ReaderSettings?>

    suspend fun saveSeriesSettings(seriesId: String, settings: ReaderSettings)

    /** 작품 설정을 지워 기본값으로 되돌린다 */
    suspend fun clearSeriesSettings(seriesId: String)
}

/**
 * DataStore 기반 구현. 저장된 값이 없거나 알 수 없는 값이면 [ReaderSettings]의 기본값(Komelia와 같음)을 쓴다.
 * 작품 설정은 같은 저장소에 "series:<id>" 키로 한 줄씩 ([ReaderSettingsCodec]).
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
        val KEY_IMAGE_ENHANCEMENT = stringPreferencesKey("image_enhancement")
        val KEY_EPUB_TEXT_ZOOM = intPreferencesKey("epub_text_zoom")
        val DEFAULTS = ReaderSettings()

        fun seriesKey(seriesId: String) = stringPreferencesKey("series:$seriesId")
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
            imageEnhancement = enumOrNull<ImageEnhancement>(prefs[KEY_IMAGE_ENHANCEMENT]) ?: DEFAULTS.imageEnhancement,
            epubTextZoom = prefs[KEY_EPUB_TEXT_ZOOM] ?: DEFAULTS.epubTextZoom,
        )
    }

    override suspend fun saveReaderSettings(settings: ReaderSettings) {
        context.readerDataStore.edit {
            it[KEY_READER_TYPE] = settings.readerType.name
            it[KEY_PAGE_LAYOUT] = settings.pageLayout.name
            it[KEY_SCALE_TYPE] = settings.scaleType.name
            it[KEY_STRETCH_TO_FIT] = settings.stretchToFit
            it[KEY_READING_DIRECTION] = settings.readingDirection.name
            it[KEY_CONTINUOUS_PADDING] = settings.continuousPadding
            it[KEY_CONTINUOUS_PAGE_SPACING] = settings.continuousPageSpacing
            it[KEY_IMAGE_ENHANCEMENT] = settings.imageEnhancement.name
        }
    }

    override suspend fun saveEpubTextZoom(zoom: Int) {
        context.readerDataStore.edit { it[KEY_EPUB_TEXT_ZOOM] = zoom }
    }

    override fun seriesSettings(seriesId: String): Flow<ReaderSettings?> =
        context.readerDataStore.data.map { prefs -> ReaderSettingsCodec.decode(prefs[seriesKey(seriesId)]) }

    override suspend fun saveSeriesSettings(seriesId: String, settings: ReaderSettings) {
        context.readerDataStore.edit { it[seriesKey(seriesId)] = ReaderSettingsCodec.encode(settings) }
    }

    override suspend fun clearSeriesSettings(seriesId: String) {
        context.readerDataStore.edit { it.remove(seriesKey(seriesId)) }
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
