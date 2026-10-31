package com.komgatv.app

import android.app.Application
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.bitmapConfig
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Call
import timber.log.Timber
import javax.inject.Inject

/**
 * KomgaTV 애플리케이션 클래스
 *
 * Hilt 의존성 주입과 Coil 3 이미지 로더를 초기화합니다.
 * TV 환경에 최적화된 이미지 캐시 및 OkHttpClient 연결 설정을 적용합니다.
 */
@HiltAndroidApp
class KomgaTVApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var callFactory: Call.Factory

    override fun onCreate() {
        super.onCreate()

        // 디버그 빌드에서만 Timber 로깅 활성화
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    /**
     * TV에 최적화된 Coil 3 ImageLoader 생성
     *
     * - 인증 및 연결 풀 공유: 세션의 OkHttpClient 기반 OkHttpNetworkFetcherFactory
     * - 메모리 캐시: 가용 메모리의 20%
     * - 디스크 캐시: 100MB
     * - RGB_565: 메모리 절약 (TV에서는 알파 채널 불필요)
     * - crossfade 비활성화: TV에서는 즉시 표시가 더 자연스러움
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { this@KomgaTVApp.callFactory }
                    )
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565)
            .crossfade(false)
            .build()
    }
}
