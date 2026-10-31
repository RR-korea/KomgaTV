package com.komgatv.core.network.di

import com.komgatv.core.network.ServerSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Call
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * 네트워크 의존성 주입 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 세션을 따라가는 HTTP 호출 팩토리 (Coil 이미지 로더용)
     *
     * 요청마다 현재 세션의 OkHttpClient로 보내므로 인증 헤더가 붙고,
     * 서버 설정이 바뀌어도 앱을 다시 시작할 필요가 없다. 세션이 없으면 인증 없이 보낸다.
     */
    @Provides
    @Singleton
    fun provideSessionCallFactory(sessionManager: ServerSessionManager): Call.Factory {
        val unauthenticated = OkHttpClient()
        return Call.Factory { request ->
            (sessionManager.session.value?.okHttpClient ?: unauthenticated).newCall(request)
        }
    }
}
