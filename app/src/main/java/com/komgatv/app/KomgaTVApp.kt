package com.komgatv.app

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath
import timber.log.Timber

/**
 * KomgaTV 애플리케이션 클래스
 *
 * Hilt 의존성 주입과 Coil 이미지 로더를 초기화합니다.
 * TV 환경에 최적화된 이미지 캐시 설정을 적용합니다.
 */
@HiltAndroidApp
class KomgaTVApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // 디버그 빌드에서만 Timber 로깅 활성화
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    /**
     * TV에 최적화된 Coil ImageLoader 생성
     *
     * - 메모리 캐시: 가용 메모리의 20%
     * - 디스크 캐시: 100MB
     * - RGB_565: 메모리 절약 (TV에서는 알파 채널 불필요)
     * - crossfade 비활성화: TV에서는 즉시 표시가 더 자연스러움
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20) // 메모리의 20% 사용
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB 디스크 캐시
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565) // 메모리 절약용 비트맵 설정
            .crossfade(false) // TV UI에서는 크로스페이드 비활성화
            .respectCacheHeaders(false) // 서버 캐시 헤더 무시하여 적극적 캐싱
            .build()
    }
}
