package com.komgatv.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.datastore.ReaderPreferences
import com.komgatv.core.datastore.SavedServer
import com.komgatv.core.datastore.ServerPreferences
import com.komgatv.core.model.ReaderSettings
import com.komgatv.core.network.AuthMode
import com.komgatv.core.network.ServerConnectionException
import com.komgatv.core.network.ServerSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * 서버 연결 테스트 및 검증 결과
 */
sealed interface ConnectionResult {
    data object None : ConnectionResult
    data object Testing : ConnectionResult
    data object Success : ConnectionResult
    sealed interface Failed : ConnectionResult {
        data object InvalidUrl : Failed
        data object Unreachable : Failed
        data object Unauthorized : Failed
        data object SslError : Failed
        data object MissingFields : Failed
        data class Other(val message: String? = null) : Failed
    }
}

/**
 * 설정 화면 UI 상태
 */
data class SettingsUiState(
    val serverUrl: String = "",
    val authMethod: String = "basic",
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val connectionResult: ConnectionResult = ConnectionResult.None,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val cacheCleared: Boolean = false,
    /** 저장된 서버로 바꿨다 — 화면이 홈부터 다시 연다 */
    val serverSwitched: Boolean = false,
)

/**
 * 설정 화면 ViewModel
 *
 * Komga 서버 연결 설정의 로드, 실시간 입력 상태 관리, 연결 검증, 저장 및 캐시 삭제를 담당합니다.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val serverPreferences: ServerPreferences,
    private val sessionManager: ServerSessionManager,
    private val readerPreferences: ReaderPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** 보기 기본값 — 작품에 따로 저장된 설정이 없으면 리더가 이것을 쓴다 */
    val viewDefaults: StateFlow<ReaderSettings> = readerPreferences.readerSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    fun updateViewDefaults(settings: ReaderSettings) {
        viewModelScope.launch { readerPreferences.saveReaderSettings(settings) }
    }

    /** 연결에 성공했던 서버들 (지금 서버 포함) */
    val savedServers: StateFlow<List<SavedServer>> = serverPreferences.savedServers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    companion object {
        /** 서버를 바꾼 뒤 세션이 새 서버를 가리킬 때까지 기다리는 최대 시간 */
        const val SWITCH_WAIT_MILLIS = 5_000L
    }

    init {
        loadSettings()
    }

    /** 저장된 서버 설정 로드 */
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val serverUrl = serverPreferences.serverUrl.first()
                val authMethod = serverPreferences.authMethod.first()
                val authConfig = serverPreferences.authConfig.first()

                _uiState.value = _uiState.value.copy(
                    serverUrl = serverUrl,
                    authMethod = if (authMethod.isNotBlank()) authMethod else "basic",
                    username = authConfig.username,
                    password = authConfig.password,
                    apiKey = authConfig.apiKey,
                )
            } catch (e: Exception) {
                Timber.e(e, "설정 로드 실패")
            }
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            serverUrl = url,
            connectionResult = ConnectionResult.None,
            saveSuccess = false,
        )
    }

    fun updateAuthMethod(method: String) {
        _uiState.value = _uiState.value.copy(
            authMethod = method,
            connectionResult = ConnectionResult.None,
            saveSuccess = false,
        )
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(
            username = username,
            connectionResult = ConnectionResult.None,
            saveSuccess = false,
        )
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            connectionResult = ConnectionResult.None,
            saveSuccess = false,
        )
    }

    fun updateApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(
            apiKey = apiKey,
            connectionResult = ConnectionResult.None,
            saveSuccess = false,
        )
    }

    /**
     * 현재 입력된 값으로 서버 연결만 테스트 (저장하지 않음)
     */
    fun testConnection() {
        viewModelScope.launch {
            val state = _uiState.value
            val url = state.serverUrl.trim()
            val auth = when (state.authMethod) {
                "apikey" -> {
                    if (url.isBlank() || state.apiKey.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            connectionResult = ConnectionResult.Failed.MissingFields
                        )
                        return@launch
                    }
                    AuthMode.ApiKey(state.apiKey.trim())
                }
                else -> {
                    if (url.isBlank() || state.username.isBlank() || state.password.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            connectionResult = ConnectionResult.Failed.MissingFields
                        )
                        return@launch
                    }
                    AuthMode.Basic(state.username.trim(), state.password)
                }
            }

            _uiState.value = _uiState.value.copy(connectionResult = ConnectionResult.Testing)

            try {
                val tempSession = sessionManager.buildSession(url, auth)
                tempSession.komgaClient.userClient().getMe()
                _uiState.value = _uiState.value.copy(connectionResult = ConnectionResult.Success)
            } catch (e: Throwable) {
                Timber.e(e, "연결 테스트 실패")
                _uiState.value = _uiState.value.copy(
                    connectionResult = mapExceptionToConnectionResult(e)
                )
            }
        }
    }

    /**
     * 서버 연결을 검증하고 성공 시 설정을 영구 저장합니다.
     */
    fun saveSettings() {
        viewModelScope.launch {
            val state = _uiState.value
            val url = state.serverUrl.trim()
            val auth = when (state.authMethod) {
                "apikey" -> {
                    if (url.isBlank() || state.apiKey.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            connectionResult = ConnectionResult.Failed.MissingFields
                        )
                        return@launch
                    }
                    AuthMode.ApiKey(state.apiKey.trim())
                }
                else -> {
                    if (url.isBlank() || state.username.isBlank() || state.password.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            connectionResult = ConnectionResult.Failed.MissingFields
                        )
                        return@launch
                    }
                    AuthMode.Basic(state.username.trim(), state.password)
                }
            }

            _uiState.value = _uiState.value.copy(
                isSaving = true,
                connectionResult = ConnectionResult.Testing,
            )

            val result = sessionManager.connect(url, auth)
            if (result.isSuccess) {
                Timber.d("서버 연결 및 저장 성공: %s", url)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    connectionResult = ConnectionResult.Success,
                    saveSuccess = true,
                )
            } else {
                val ex = result.exceptionOrNull() ?: Exception("Unknown error")
                Timber.e(ex, "서버 연결 및 저장 실패: %s", url)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    connectionResult = mapExceptionToConnectionResult(ex),
                    saveSuccess = false,
                )
            }
        }
    }

    private fun mapExceptionToConnectionResult(e: Throwable): ConnectionResult.Failed {
        return when (val ex = (e as? ServerConnectionException) ?: sessionManager.classifyException(e)) {
            is ServerConnectionException.InvalidUrl -> ConnectionResult.Failed.InvalidUrl
            is ServerConnectionException.Unreachable -> ConnectionResult.Failed.Unreachable
            is ServerConnectionException.Unauthorized -> ConnectionResult.Failed.Unauthorized
            is ServerConnectionException.SslError -> ConnectionResult.Failed.SslError
            is ServerConnectionException.Other -> ConnectionResult.Failed.Other(ex.message)
        }
    }

    /**
     * 저장된 서버로 바꾼다. 세션이 새 서버를 가리키면(또는 잠시 뒤) 화면이 홈부터 다시 연다.
     */
    fun switchServer(serverId: String) {
        viewModelScope.launch {
            val target = savedServers.value.firstOrNull { it.id == serverId } ?: return@launch
            if (!serverPreferences.switchTo(serverId)) return@launch
            // 옛 서버로 홈을 불러오지 않게, 세션이 바뀔 때까지 기다린다
            withTimeoutOrNull(SWITCH_WAIT_MILLIS) {
                sessionManager.session.first { it?.baseUrl == target.url.trimEnd('/') }
            }
            _uiState.value = _uiState.value.copy(serverSwitched = true)
        }
    }

    /** 저장된 서버를 지운다 (지금 서버는 저장소가 거절한다) */
    fun removeServer(serverId: String) {
        viewModelScope.launch {
            serverPreferences.removeServer(serverId)
        }
    }

    /** 새 서버를 입력할 수 있게 입력칸을 비운다 */
    fun startNewServer() {
        _uiState.value = _uiState.value.copy(
            serverUrl = "",
            authMethod = "basic",
            username = "",
            password = "",
            apiKey = "",
            connectionResult = ConnectionResult.None,
            saveSuccess = false,
        )
    }

    /**
     * 이미지 디스크 캐시 삭제
     */
    fun clearImageCache() {
        viewModelScope.launch {
            try {
                val cacheDir = File(context.cacheDir, "image_cache")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
                _uiState.value = _uiState.value.copy(cacheCleared = true)
            } catch (e: Exception) {
                Timber.e(e, "이미지 캐시 삭제 실패")
            }
        }
    }
}
