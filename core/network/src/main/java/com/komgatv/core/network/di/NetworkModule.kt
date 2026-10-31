package com.komgatv.core.network.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.komgatv.core.data.preferences.ServerPreferences
import com.komgatv.core.network.KomgaApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 네트워크 의존성 주입 모듈
 *
 * OkHttpClient, Retrofit, KomgaApi 인스턴스를 Hilt를 통해 제공합니다.
 * Basic Auth 인터셉터와 kotlinx.serialization 컨버터를 포함합니다.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * kotlinx.serialization Json 인스턴스 제공
     *
     * 알 수 없는 키를 무시하고 관대한 파싱을 적용합니다.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true    // 서버 응답에 새 필드가 추가되어도 안전
        coerceInputValues = true    // null 값을 기본값으로 변환
        isLenient = true            // 관대한 JSON 파싱
    }

    /**
     * 인증 인터셉터 제공
     *
     * ServerPreferences에서 저장된 인증 정보를 읽어
     * 모든 API 요청에 Basic Auth 또는 API Key 헤더를 추가합니다.
     */
    @Provides
    @Singleton
    fun provideAuthInterceptor(
        serverPreferences: ServerPreferences,
    ): Interceptor = Interceptor { chain ->
        // 저장된 인증 정보 조회
        val authConfig = runBlocking {
            serverPreferences.authConfig.first()
        }

        val requestBuilder = chain.request().newBuilder()

        // 인증 방식에 따라 헤더 추가
        when {
            // Basic Auth 방식
            authConfig.username.isNotEmpty() && authConfig.password.isNotEmpty() -> {
                val credentials = Credentials.basic(authConfig.username, authConfig.password)
                requestBuilder.addHeader("Authorization", credentials)
            }
            // API Key 방식
            authConfig.apiKey.isNotEmpty() -> {
                requestBuilder.addHeader("X-Auth-Token", authConfig.apiKey)
            }
        }

        chain.proceed(requestBuilder.build())
    }

    /**
     * OkHttpClient 제공
     *
     * 인증 인터셉터와 적절한 타임아웃 설정을 포함합니다.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: Interceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // 큰 이미지 로딩을 위해 넉넉한 타임아웃
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrofit 인스턴스 제공
     *
     * 서버 URL은 ServerPreferences에서 동적으로 가져옵니다.
     * kotlinx.serialization 컨버터를 사용합니다.
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        serverPreferences: ServerPreferences,
    ): Retrofit {
        // 저장된 서버 URL 조회 (기본값: localhost)
        val baseUrl = runBlocking {
            serverPreferences.serverUrl.first().ifEmpty { "http://localhost:25600" }
        }.let { url ->
            // URL이 /로 끝나지 않으면 추가
            if (url.endsWith("/")) url else "$url/"
        }

        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    /**
     * KomgaApi 인스턴스 제공
     */
    @Provides
    @Singleton
    fun provideKomgaApi(retrofit: Retrofit): KomgaApi {
        return retrofit.create(KomgaApi::class.java)
    }
}
