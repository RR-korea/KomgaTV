package com.komgatv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.komgatv.app.navigation.KomgaTVNavHost
import com.komgatv.app.ui.theme.KomgaTVTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * KomgaTV 메인 액티비티
 *
 * Android TV의 진입점으로, Compose UI 트리를 설정합니다.
 * 리더 등 개별 화면의 리모컨 키 이벤트는 각 컴포저블의 onPreviewKeyEvent로 처리합니다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KomgaTVTheme {
                KomgaTVNavHost()
            }
        }
    }
}
