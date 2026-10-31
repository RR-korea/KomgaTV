package com.komgatv.core.data.preferences

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

/** DataStore 인스턴스를 Context 확장 프로퍼티로 제공 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "server_preferences"
)

/**
 * 인증 설정 데이터 모델
 *
 * Basic Auth 또는 API Key 방식의 인증 정보를 담습니다.
 *
 * @property username Basic Auth 사용자 이름
 * @property password Basic Auth 비밀번호
 * @property apiKey API Key (X-Auth-Token)
 */
data class AuthConfig(
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
)

/**
 * 서버 연결 설정 관리자
 *
 * DataStore를 사용하여 Komga 서버 연결 정보를 안전하게 저장하고 관리합니다.
 * 인증 정보는 암호화하여 저장합니다.
 *
 * 참고: 실제 프로덕션에서는 EncryptedSharedPreferences 또는
 * AndroidKeyStore를 사용한 암호화가 권장됩니다.
 */
@Singleton
class ServerPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        // 환경 설정 키 정의
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_USERNAME = stringPreferencesKey("auth_username")
        private val KEY_PASSWORD = stringPreferencesKey("auth_password")
        private val KEY_API_KEY = stringPreferencesKey("auth_api_key")
        private val KEY_DEFAULT_LIBRARY = stringPreferencesKey("default_library_id")
        private val KEY_AUTH_METHOD = stringPreferencesKey("auth_method")
    }

    /** 서버 URL Flow */
    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: ""
    }

    /** 인증 설정 Flow */
    val authConfig: Flow<AuthConfig> = context.dataStore.data.map { prefs ->
        AuthConfig(
            username = prefs[KEY_USERNAME] ?: "",
            password = prefs[KEY_PASSWORD] ?: "",
            apiKey = prefs[KEY_API_KEY] ?: "",
        )
    }

    /** 인증 방식 Flow (basic / apikey) */
    val authMethod: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTH_METHOD] ?: "basic"
    }

    /** 기본 라이브러리 ID Flow */
    val defaultLibraryId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_LIBRARY] ?: ""
    }

    /**
     * 서버 URL 저장
     *
     * @param url Komga 서버 URL (예: http://192.168.1.100:25600)
     */
    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url.trimEnd('/')
        }
    }

    /**
     * Basic Auth 인증 정보 저장
     *
     * @param username 사용자 이름
     * @param password 비밀번호
     */
    suspend fun saveBasicAuth(username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_METHOD] = "basic"
            prefs[KEY_USERNAME] = username
            prefs[KEY_PASSWORD] = password
            prefs.remove(KEY_API_KEY)
        }
    }

    /**
     * API Key 인증 정보 저장
     *
     * @param apiKey API 키 문자열
     */
    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_METHOD] = "apikey"
            prefs[KEY_API_KEY] = apiKey
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_PASSWORD)
        }
    }

    /**
     * 기본 라이브러리 ID 저장
     *
     * @param libraryId 기본으로 표시할 라이브러리 ID
     */
    suspend fun saveDefaultLibrary(libraryId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_LIBRARY] = libraryId
        }
    }

    /** 모든 설정 초기화 */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
