package com.komgatv.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.core.data.preferences.ServerPreferences
import com.komgatv.core.network.KomgaApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * 연결 테스트 상태
 */
enum class ConnectionStatus {
    None,       // 테스트 전
    Testing,    // 테스트 중
    Success,    // 연결 성공
    Failed,     // 연결 실패
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
    val connectionStatus: ConnectionStatus = ConnectionStatus.None,
)

/**
 * 설정 화면 ViewModel
 *
 * 서버 연결 설정의 저장, 로드, 연결 테스트, 캐시 관리를 담당합니다.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val serverPreferences: ServerPreferences,
    private val komgaApi: KomgaApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    /** 저장된 설정 로드 */
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val serverUrl = serverPreferences.serverUrl.first()
                val authMethod = serverPreferences.authMethod.first()
                val authConfig = serverPreferences.authConfig.first()

                _uiState.value = SettingsUiState(
                    serverUrl = serverUrl,
                    authMethod = authMethod,
                    username = authConfig.username,
                    password = authConfig.password,
                    apiKey = authConfig.apiKey,
                )
            } catch (e: Exception) {
                Timber.e(e, "설정 로드 실패")
            }
        }
    }

    /**
     * 설정 저장
     *
     * @param serverUrl 서버 URL
     * @param authMethod 인증 방식 (basic / apikey)
     * @param username Basic Auth 사용자명
     * @param password Basic Auth 비밀번호
     * @param apiKey API 키
     */
    suspend fun saveSettings(
        serverUrl: String,
        authMethod: String,
        username: String = "",
        password: String = "",
        apiKey: String = "",
    ) {
        try {
            // 서버 URL 저장
            serverPreferences.saveServerUrl(serverUrl)

            // 인증 정보 저장
            when (authMethod) {
                "basic" -> serverPreferences.saveBasicAuth(username, password)
                "apikey" -> serverPreferences.saveApiKey(apiKey)
            }

            // UI 상태 업데이트
            _uiState.value = _uiState.value.copy(
                serverUrl = serverUrl,
                authMethod = authMethod,
                username = username,
                password = password,
                apiKey = apiKey,
            )

            Timber.d("설정 저장 완료: $serverUrl, 인증 방식: $authMethod")
        } catch (e: Exception) {
            Timber.e(e, "설정 저장 실패")
        }
    }

    /**
     * 서버 연결 테스트
     *
     * 라이브러리 목록 API를 호출하여 서버 연결 가능 여부를 확인합니다.
     */
    suspend fun testConnection() {
        _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Testing)

        try {
            // 라이브러리 목록 조회로 연결 테스트
            val libraries = komgaApi.getLibraries()
            Timber.d("연결 테스트 성공: ${libraries.size}개 라이브러리 발견")
            _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Success)
        } catch (e: Exception) {
            Timber.e(e, "연결 테스트 실패")
            _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Failed)
        }
    }

    /**
     * 이미지 캐시 삭제
     *
     * Coil의 디스크 캐시 디렉터리를 삭제합니다.
     */
    suspend fun clearImageCache() {
        try {
            val cacheDir = File(context.cacheDir, "image_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Timber.d("이미지 캐시 삭제 완료: ${cacheDir.absolutePath}")
            }
        } catch (e: Exception) {
            Timber.e(e, "이미지 캐시 삭제 실패")
        }
    }
}
