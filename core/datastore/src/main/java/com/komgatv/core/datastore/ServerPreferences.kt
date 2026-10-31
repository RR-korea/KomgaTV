package com.komgatv.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.komgatv.core.datastore.crypto.CredentialEncryptor
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
 * @property username Basic Auth 사용자 이름
 * @property password Basic Auth 비밀번호 (복호화된 평문)
 * @property apiKey API Key (X-API-Key, 복호화된 평문)
 */
data class AuthConfig(
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
)

/**
 * 서버 전체 설정 데이터 모델
 */
data class ServerConfig(
    val serverUrl: String = "",
    val authMethod: String = "basic",
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val defaultLibraryId: String = "",
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && (
            (authMethod == "basic" && username.isNotBlank() && password.isNotBlank()) ||
                (authMethod == "apikey" && apiKey.isNotBlank())
            )
}

/**
 * 서버 연결 설정 인터페이스
 */
interface ServerPreferences {
    val serverConfig: Flow<ServerConfig>
    val serverUrl: Flow<String>
    val authConfig: Flow<AuthConfig>
    val authMethod: Flow<String>
    val defaultLibraryId: Flow<String>

    suspend fun saveServerUrl(url: String)
    suspend fun saveBasicAuth(username: String, password: String)
    suspend fun saveApiKey(apiKey: String)
    suspend fun saveDefaultLibrary(libraryId: String)
    suspend fun clearAll()
}

/**
 * DataStore 기반 서버 연결 설정 구현체
 *
 * Keystore AES-GCM 암호화를 결합하여 Komga 서버 연결 정보를 안전하게 보관합니다.
 */
@Singleton
class DefaultServerPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialEncryptor: CredentialEncryptor,
) : ServerPreferences {

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_USERNAME = stringPreferencesKey("auth_username")
        private val KEY_PASSWORD = stringPreferencesKey("auth_password")
        private val KEY_API_KEY = stringPreferencesKey("auth_api_key")
        private val KEY_DEFAULT_LIBRARY = stringPreferencesKey("default_library_id")
        private val KEY_AUTH_METHOD = stringPreferencesKey("auth_method")
    }

    /** 전체 서버 설정 Flow (비밀번호와 API Key는 복호화되어 제공) */
    override val serverConfig: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        val rawPassword = prefs[KEY_PASSWORD] ?: ""
        val rawApiKey = prefs[KEY_API_KEY] ?: ""
        val decryptedPassword = if (rawPassword.isNotEmpty()) credentialEncryptor.decrypt(rawPassword) else ""
        val decryptedApiKey = if (rawApiKey.isNotEmpty()) credentialEncryptor.decrypt(rawApiKey) else ""

        ServerConfig(
            serverUrl = prefs[KEY_SERVER_URL] ?: "",
            authMethod = prefs[KEY_AUTH_METHOD] ?: "basic",
            username = prefs[KEY_USERNAME] ?: "",
            password = decryptedPassword,
            apiKey = decryptedApiKey,
            defaultLibraryId = prefs[KEY_DEFAULT_LIBRARY] ?: "",
        )
    }

    /** 서버 URL Flow */
    override val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: ""
    }

    /** 인증 설정 Flow (비밀번호와 API Key는 복호화되어 제공) */
    override val authConfig: Flow<AuthConfig> = context.dataStore.data.map { prefs ->
        val rawPassword = prefs[KEY_PASSWORD] ?: ""
        val rawApiKey = prefs[KEY_API_KEY] ?: ""
        val decryptedPassword = if (rawPassword.isNotEmpty()) credentialEncryptor.decrypt(rawPassword) else ""
        val decryptedApiKey = if (rawApiKey.isNotEmpty()) credentialEncryptor.decrypt(rawApiKey) else ""

        AuthConfig(
            username = prefs[KEY_USERNAME] ?: "",
            password = decryptedPassword,
            apiKey = decryptedApiKey,
        )
    }

    /** 인증 방식 Flow (basic / apikey) */
    override val authMethod: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTH_METHOD] ?: "basic"
    }

    /** 기본 라이브러리 ID Flow */
    override val defaultLibraryId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_LIBRARY] ?: ""
    }

    /**
     * 서버 URL 저장
     */
    override suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url.trimEnd('/')
        }
    }

    /**
     * Basic Auth 인증 정보 저장 (비밀번호는 Keystore AES-GCM으로 암호화하여 저장)
     */
    override suspend fun saveBasicAuth(username: String, password: String) {
        val encryptedPassword = if (password.isNotEmpty()) credentialEncryptor.encrypt(password) else ""
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_METHOD] = "basic"
            prefs[KEY_USERNAME] = username
            prefs[KEY_PASSWORD] = encryptedPassword
            prefs.remove(KEY_API_KEY)
        }
    }

    /**
     * API Key 인증 정보 저장 (API Key는 Keystore AES-GCM으로 암호화하여 저장)
     */
    override suspend fun saveApiKey(apiKey: String) {
        val encryptedApiKey = if (apiKey.isNotEmpty()) credentialEncryptor.encrypt(apiKey) else ""
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_METHOD] = "apikey"
            prefs[KEY_API_KEY] = encryptedApiKey
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_PASSWORD)
        }
    }

    /**
     * 기본 라이브러리 ID 저장
     */
    override suspend fun saveDefaultLibrary(libraryId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_LIBRARY] = libraryId
        }
    }

    /** 모든 설정 초기화 */
    override suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
