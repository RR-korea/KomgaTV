package com.komgatv.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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

    /** 연결에 성공했던 서버들 (지금 서버 포함). 연결을 저장하면 자동으로 더해진다. */
    val savedServers: Flow<List<SavedServer>>

    /** 저장된 서버로 바꾼다 — 지금 연결 설정이 그 서버로 바뀐다. 없는 ID면 false. */
    suspend fun switchTo(serverId: String): Boolean

    /** 저장된 서버를 지운다. 지금 연결된 서버는 지우지 않는다. */
    suspend fun removeServer(serverId: String)
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
        private val KEY_SAVED_SERVERS = stringPreferencesKey("saved_servers")
    }

    /** 지금 연결 설정(암호화된 비밀 그대로)을 저장 서버 형태로. 설정이 없으면 null. */
    private fun activeStored(prefs: Preferences): StoredServer? {
        val url = prefs[KEY_SERVER_URL].orEmpty()
        if (url.isBlank()) return null
        val method = prefs[KEY_AUTH_METHOD] ?: "basic"
        val secret = (if (method == "apikey") prefs[KEY_API_KEY] else prefs[KEY_PASSWORD]).orEmpty()
        if (secret.isBlank()) return null
        val user = if (method == "apikey") "" else prefs[KEY_USERNAME].orEmpty()
        return StoredServer(url, method, user, secret)
    }

    /** 지금 연결 설정을 저장 서버 목록에 넣는다 (같은 서버·계정이면 바꾼다) */
    private fun rememberActive(prefs: MutablePreferences) {
        val active = activeStored(prefs) ?: return
        val list = SavedServerCodec.decode(prefs[KEY_SAVED_SERVERS])
        prefs[KEY_SAVED_SERVERS] = SavedServerCodec.encode(SavedServerCodec.upsert(list, active))
    }

    override val savedServers: Flow<List<SavedServer>> = context.dataStore.data.map { prefs ->
        val active = activeStored(prefs)
        // 예전 버전에서 연결한 서버는 목록에 없을 수 있다 — 지금 서버는 늘 보인다
        val list = SavedServerCodec.decode(prefs[KEY_SAVED_SERVERS])
            .let { stored -> if (active != null) SavedServerCodec.upsert(stored, active) else stored }
        list.map { SavedServer(it.id, it.url, it.authMethod, it.username, isActive = it.id == active?.id) }
    }

    override suspend fun switchTo(serverId: String): Boolean {
        var switched = false
        context.dataStore.edit { prefs ->
            rememberActive(prefs)
            val target = SavedServerCodec.decode(prefs[KEY_SAVED_SERVERS]).firstOrNull { it.id == serverId }
                ?: return@edit
            prefs[KEY_SERVER_URL] = target.url
            prefs[KEY_AUTH_METHOD] = target.authMethod
            if (target.authMethod == "apikey") {
                prefs[KEY_API_KEY] = target.secret
                prefs.remove(KEY_USERNAME)
                prefs.remove(KEY_PASSWORD)
            } else {
                prefs[KEY_USERNAME] = target.username
                prefs[KEY_PASSWORD] = target.secret
                prefs.remove(KEY_API_KEY)
            }
            // 기본 라이브러리는 서버마다 다르다
            prefs.remove(KEY_DEFAULT_LIBRARY)
            switched = true
        }
        return switched
    }

    override suspend fun removeServer(serverId: String) {
        context.dataStore.edit { prefs ->
            if (activeStored(prefs)?.id == serverId) return@edit
            val list = SavedServerCodec.decode(prefs[KEY_SAVED_SERVERS]).filterNot { it.id == serverId }
            prefs[KEY_SAVED_SERVERS] = SavedServerCodec.encode(list)
        }
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
            rememberActive(prefs)
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
            rememberActive(prefs)
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
