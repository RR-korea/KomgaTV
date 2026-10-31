package com.komgatv.core.data.cache

import com.komgatv.core.data.error.HttpStatusException
import com.komgatv.core.data.image.ImageUrls
import com.komgatv.core.data.repository.NotConnectedException
import com.komgatv.core.network.ServerSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PageFileCache] 구현 — 세션 OkHttpClient로 받아 [PageCacheConfig.directory]에 저장하고,
 * 상한을 넘으면 마지막으로 쓴 지 오래된 파일부터 지운다(LRU, 파일 수정 시각 기준).
 *
 * 같은 페이지 요청만 서로 기다리고 다른 페이지는 동시에 받는다.
 */
@Singleton
class DefaultPageFileCache @Inject constructor(
    private val config: PageCacheConfig,
    private val sessionManager: ServerSessionManager,
    private val imageUrls: ImageUrls,
) : PageFileCache {

    private val pageLocks = ConcurrentHashMap<String, Mutex>()
    private val evictLock = Mutex()

    override suspend fun getPageFile(bookId: String, pageNumber: Int): File {
        val key = "${bookId}_$pageNumber"
        return pageLocks.getOrPut(key) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                val dir = config.directory.apply { mkdirs() }
                val target = File(dir, "$key.bin")
                if (target.length() > 0) {
                    target.setLastModified(System.currentTimeMillis())
                    return@withContext target
                }
                download(bookId, pageNumber, target)
                evictIfNeeded(dir)
                target
            }
        }
    }

    private suspend fun download(bookId: String, pageNumber: Int, target: File) {
        val client = sessionManager.session.value?.okHttpClient ?: throw NotConnectedException()
        val url = imageUrls.page(bookId, pageNumber) ?: throw NotConnectedException()
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            // 취소되면 인터럽트로 OkHttp 호출을 끊는다
            runInterruptible {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw HttpStatusException(response.code)
                    response.body.byteStream().use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    }
                }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
            }
        } finally {
            temp.delete()
        }
    }

    private suspend fun evictIfNeeded(dir: File) = evictLock.withLock {
        val files = dir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") } ?: return@withLock
        var total = files.sumOf { it.length() }
        if (total <= config.maxSizeBytes) return@withLock
        for (file in files.sortedBy { it.lastModified() }) {
            val size = file.length()
            if (file.delete()) {
                total -= size
                Timber.d("페이지 캐시 삭제: %s (%d bytes)", file.name, size)
            }
            if (total <= config.maxSizeBytes) break
        }
    }
}
