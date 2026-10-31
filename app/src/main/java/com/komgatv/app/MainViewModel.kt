package com.komgatv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgatv.app.navigation.Screen
import com.komgatv.core.datastore.ServerPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import javax.inject.Inject

/**
 * 앱 시작 화면 상태
 */
sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val startDestination: String) : MainUiState
}

/**
 * 메인 액티비티 ViewModel
 *
 * 앱을 켤 때 저장된 서버 설정을 한 번 읽어 시작 화면(없음: 설정, 있음: 홈)을 정한다.
 * 실행 중 설정이 바뀌어도 다시 정하지 않는다 — 시작 화면이 바뀌면 내비게이션 그래프가 통째로 재생성된다.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    serverPreferences: ServerPreferences,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = serverPreferences.serverConfig
        .take(1)
        .map { config ->
            val startDestination = if (config.isConfigured) {
                Screen.Home.route
            } else {
                Screen.Settings.route
            }
            MainUiState.Ready(startDestination)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MainUiState.Loading,
        )
}
