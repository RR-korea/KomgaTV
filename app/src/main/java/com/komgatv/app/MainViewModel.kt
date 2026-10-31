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
 * 서버 설정 여부를 감지하여 시작 화면(세션 없음: 설정, 세션 있음: 홈)을 결정합니다.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    serverPreferences: ServerPreferences,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = serverPreferences.serverConfig
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
