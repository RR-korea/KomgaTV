package com.komgatv.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.homeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "home_preferences"
)

/**
 * 홈 화면 — "읽는 중"에서 뺀 시리즈.
 *
 * 서버의 읽기 진행은 그대로 두고 이 TV의 홈에서만 숨긴다 ("다음 권"에도 그 시리즈를 보이지 않는다).
 * 그 시리즈를 뺀 뒤에 다시 읽으면 홈이 다시 보이게 하고 기록을 지운다.
 */
interface HomePreferences {
    /** 뺀 시리즈 ID → 뺀 시각 (epoch ms) */
    val hiddenReadingSeries: Flow<Map<String, Long>>

    suspend fun hideReadingSeries(seriesId: String, atMillis: Long)

    suspend fun unhideReadingSeries(seriesIds: Set<String>)
}

@Singleton
class DefaultHomePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : HomePreferences {

    private companion object {
        val KEY_HIDDEN_READING = stringPreferencesKey("hidden_reading_series")
    }

    override val hiddenReadingSeries: Flow<Map<String, Long>> =
        context.homeDataStore.data.map { HiddenSeriesCodec.decode(it[KEY_HIDDEN_READING]) }

    override suspend fun hideReadingSeries(seriesId: String, atMillis: Long) {
        context.homeDataStore.edit {
            val hidden = HiddenSeriesCodec.decode(it[KEY_HIDDEN_READING]) + (seriesId to atMillis)
            it[KEY_HIDDEN_READING] = HiddenSeriesCodec.encode(hidden)
        }
    }

    override suspend fun unhideReadingSeries(seriesIds: Set<String>) {
        if (seriesIds.isEmpty()) return
        context.homeDataStore.edit {
            val hidden = HiddenSeriesCodec.decode(it[KEY_HIDDEN_READING]) - seriesIds
            it[KEY_HIDDEN_READING] = HiddenSeriesCodec.encode(hidden)
        }
    }
}

/** 뺀 시리즈 ↔ 문자열 ("id=ms;id=ms"). 잘못된 조각은 버린다 */
internal object HiddenSeriesCodec {
    fun encode(hidden: Map<String, Long>): String = hidden.entries.joinToString(";") { (id, at) -> "$id=$at" }

    fun decode(text: String?): Map<String, Long> {
        if (text.isNullOrBlank()) return emptyMap()
        return text.split(";").mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val at = part.substring(i + 1).toLongOrNull() ?: return@mapNotNull null
            part.substring(0, i) to at
        }.toMap()
    }
}
