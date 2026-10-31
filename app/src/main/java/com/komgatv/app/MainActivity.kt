package com.komgatv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komgatv.app.navigation.KomgaTVNavHost
import com.komgatv.app.ui.theme.KomgaTVTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * KomgaTV 메인 액티비티
 *
 * 저장된 서버 설정 여부로 시작 화면을 정한 뒤 내비게이션을 띄운다.
 * 리모컨 키는 각 화면이 포커스 기반으로 처리한다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KomgaTVTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                // 설정을 읽는 짧은 동안은 빈 화면(테마 배경)을 둔다.
                (uiState as? MainUiState.Ready)?.let { state ->
                    KomgaTVNavHost(startDestination = state.startDestination)
                }
            }
        }
    }
}
