package com.komgatv.core.data.cache

import com.komgatv.core.data.error.HttpStatusException
import com.komgatv.core.data.image.SessionImageUrls
import com.komgatv.core.datastore.AuthConfig
import com.komgatv.core.datastore.ServerConfig
import com.komgatv.core.datastore.SavedServer
import com.komgatv.core.datastore.ServerPreferences
import com.komgatv.core.network.AuthMode
import com.komgatv.core.network.ServerSessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class FakeServerPreferences : ServerPreferences {
    val configFlow = MutableStateFlow(ServerConfig())
    val urlFlow = MutableStateFlow("")
    val authConfigFlow = MutableStateFlow(AuthConfig())
    val authMethodFlow = MutableStateFlow("basic")
    val defaultLibraryFlow = MutableStateFlow("")

    override val serverConfig: Flow<ServerConfig> = configFlow
    override val serverUrl: Flow<String> = urlFlow
    override val authConfig: Flow<AuthConfig> = authConfigFlow
    override val authMethod: Flow<String> = authMethodFlow
    override val defaultLibraryId: Flow<String> = defaultLibraryFlow

    override suspend fun saveServerUrl(url: String) {
        val clean = url.trimEnd('/')
        urlFlow.value = clean
        configFlow.value = configFlow.value.copy(serverUrl = clean)
    }

    override suspend fun saveBasicAuth(username: String, password: String) {
        authMethodFlow.value = "basic"
        authConfigFlow.value = AuthConfig(username = username, password = password, apiKey = "")
        configFlow.value = configFlow.value.copy(
            authMethod = "basic",
            username = username,
            password = password,
            apiKey = "",
        )
    }

    override suspend fun saveApiKey(apiKey: String) {
        authMethodFlow.value = "apikey"
        authConfigFlow.value = AuthConfig(username = "", password = "", apiKey = apiKey)
        configFlow.value = configFlow.value.copy(
            authMethod = "apikey",
            username = "",
            password = "",
            apiKey = apiKey,
        )
    }

    override suspend fun saveDefaultLibrary(libraryId: String) {
        defaultLibraryFlow.value = libraryId
        configFlow.value = configFlow.value.copy(defaultLibraryId = libraryId)
    }

    override suspend fun clearAll() {
        configFlow.value = ServerConfig()
        urlFlow.value = ""
        authConfigFlow.value = AuthConfig()
        authMethodFlow.value = "basic"
        defaultLibraryFlow.value = ""
    }

    override val savedServers: Flow<List<SavedServer>> = MutableStateFlow(emptyList())

    override suspend fun switchTo(serverId: String): Boolean = false

    override suspend fun removeServer(serverId: String) = Unit
}

class DefaultPageFileCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val sessionManager = ServerSessionManager(FakeServerPreferences())
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        server.start()
        cacheDir = tempFolder.newFolder("page_cache")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** API Key로 연결(/api/v2/users/me 응답을 먼저 소비)하고 캐시를 만든다 */
    private suspend fun connectedCache(apiKey: String = API_KEY, maxSize: Long = 1024 * 1024L): DefaultPageFileCache {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody(
                    """{"id":"1","email":"a@example.com","roles":[],"sharedAllLibraries":true,""" +
                        """"sharedLibrariesIds":[],"labelsAllow":[],"labelsExclude":[]}""",
                ),
        )
        check(sessionManager.connect(server.url("/").toString(), AuthMode.ApiKey(apiKey)).isSuccess)
        server.takeRequest()
        val config = object : PageCacheConfig {
            override val directory = cacheDir
            override val maxSizeBytes = maxSize
        }
        return DefaultPageFileCache(config, sessionManager, SessionImageUrls(sessionManager))
    }

    private fun enqueueImage(size: Int) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(ByteArray(size) { 1 })))
    }

    @Test
    fun `페이지를 받을 때 인증 헤더를 붙인다`() = runTest {
        val cache = connectedCache()
        enqueueImage(50)

        val file = cache.getPageFile("book1", 1)

        assertEquals(50L, file.length())
        val request = server.takeRequest()
        assertEquals("/api/v1/books/book1/pages/1", request.path)
        assertEquals(API_KEY, request.getHeader("X-API-Key"))
    }

    @Test
    fun `캐시에 있으면 다시 요청하지 않는다`() = runTest {
        val cache = connectedCache()
        enqueueImage(30)

        val first = cache.getPageFile("book1", 2)
        val second = cache.getPageFile("book1", 2)

        assertEquals(first.absolutePath, second.absolutePath)
        assertEquals(2, server.requestCount) // users/me + 페이지 1회
    }

    @Test
    fun `상한을 넘으면 오래된 파일부터 지운다`() = runTest {
        val cache = connectedCache(maxSize = 250L)

        enqueueImage(100)
        val file1 = cache.getPageFile("book1", 1).apply { setLastModified(1_000L) }
        enqueueImage(100)
        val file2 = cache.getPageFile("book1", 2).apply { setLastModified(2_000L) }
        enqueueImage(100)
        val file3 = cache.getPageFile("book1", 3)

        assertFalse(file1.exists())
        assertTrue(file2.exists())
        assertTrue(file3.exists())
    }

    @Test
    fun `성공이 아닌 응답은 HttpStatusException이고 파일을 남기지 않는다`() = runTest {
        val cache = connectedCache()
        server.enqueue(MockResponse().setResponseCode(401))

        val error = runCatching { cache.getPageFile("book1", 1) }.exceptionOrNull()

        assertEquals(401, (error as HttpStatusException).code)
        assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
    }

    private companion object {
        const val API_KEY = "secret-token-123"
    }
}
