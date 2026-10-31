package com.komgatv.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komgatv.app.navigation.KomgaTVNavHost
import com.komgatv.app.ui.theme.KomgaTVTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * D-Pad 키 이벤트 핸들러를 위한 CompositionLocal
 * 하위 컴포저블에서 키 이벤트를 처리할 수 있도록 제공
 */
val LocalKeyEventHandler = staticCompositionLocalOf<((KeyEvent) -> Boolean)?> { null }

/**
 * KomgaTV 메인 액티비티
 *
 * Android TV의 진입점으로, 서버 세션 설정 여부에 따라 시작 화면을 결정하고
 * D-Pad 키 이벤트를 처리합니다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** 컴포저블에서 등록한 키 이벤트 핸들러 */
    private var keyEventHandler: ((KeyEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KomgaTVTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                when (val state = uiState) {
                    is MainUiState.Loading -> {
                        // 세션 설정 로드 중
                    }
                    is MainUiState.Ready -> {
                        CompositionLocalProvider(
                            LocalKeyEventHandler provides keyEventHandler
                        ) {
                            KomgaTVNavHost(
                                startDestination = state.startDestination,
                                onRegisterKeyEventHandler = { handler ->
                                    keyEventHandler = handler
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 액티비티 레벨 D-Pad 키 이벤트 처리
     *
     * 등록된 핸들러가 이벤트를 소비하지 않으면 기본 처리로 전달합니다.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        event?.let {
            if (keyEventHandler?.invoke(it) == true) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
