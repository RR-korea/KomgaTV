package com.komgatv.core.data.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** 페이지 파일 캐시의 위치와 크기 상한 */
interface PageCacheConfig {
    val directory: File
    val maxSizeBytes: Long
}

/** 앱 캐시 폴더 아래 `komga_pages`, 200MB */
class DefaultPageCacheConfig @Inject constructor(
    @ApplicationContext private val context: Context,
) : PageCacheConfig {
    override val directory: File get() = File(context.cacheDir, "komga_pages")
    override val maxSizeBytes: Long = 200L * 1024 * 1024
}
