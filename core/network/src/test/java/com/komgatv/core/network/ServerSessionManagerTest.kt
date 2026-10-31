package com.komgatv.core.network

import com.komgatv.core.datastore.AuthConfig
import com.komgatv.core.datastore.ServerConfig
import com.komgatv.core.datastore.SavedServer
import com.komgatv.core.datastore.ServerPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 테스트용 인메모리 ServerPreferences 구현체
 */
class FakeServerPreferences : ServerPreferences {
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

class ServerSessionManagerTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakePreferences: FakeServerPreferences
    private lateinit var sessionManager: ServerSessionManager

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        fakePreferences = FakeServerPreferences()
        sessionManager = ServerSessionManager(fakePreferences)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun connect_success_200_returns_user() = runTest {
        mockWebServer.start()
        val userJson = """
            {
                "id": "01HABCDEF1234567",
                "email": "komga-admin@example.com",
                "roles": ["ADMIN", "PAGE_STREAMING"],
                "sharedAllLibraries": true,
                "sharedLibrariesIds": [],
                "labelsAllow": [],
                "labelsExclude": []
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(userJson)
        )

        val serverUrl = mockWebServer.url("/").toString()
        val auth = AuthMode.Basic("admin", "password123")

        val result = sessionManager.connect(serverUrl, auth)

        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertEquals("komga-admin@example.com", user?.email)

        // 요청 경로 및 인증 헤더 확인
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/v2/users/me", recordedRequest.path)
        assertTrue(recordedRequest.getHeader("Authorization")?.startsWith("Basic ") == true)

        // 설정 저장 확인
        assertEquals(serverUrl.trimEnd('/'), fakePreferences.urlFlow.value)
        assertEquals("admin", fakePreferences.authConfigFlow.value.username)
    }

    @Test
    fun connect_with_sub_path_keeps_path_prefix() = runTest {
        // 리버스 프록시 뒤 하위 경로(https://host/komga)에 있는 서버
        mockWebServer.start()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"id":"1","email":"a@example.com","roles":[],"sharedAllLibraries":true,""" +
                        """"sharedLibrariesIds":[],"labelsAllow":[],"labelsExclude":[]}"""
                )
        )

        val serverUrl = mockWebServer.url("/komga").toString()
        val result = sessionManager.connect(serverUrl, AuthMode.Basic("admin", "password123"))

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        assertEquals("/komga/api/v2/users/me", mockWebServer.takeRequest().path)
        assertEquals(serverUrl, sessionManager.session.value?.baseUrl)
    }

    @Test
    fun connect_failure_401_returns_unauthorized_exception() = runTest {
        mockWebServer.start()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":401,"error":"Unauthorized","message":"Bad credentials"}""")
        )

        val serverUrl = mockWebServer.url("/").toString()
        val auth = AuthMode.Basic("admin", "wrong-password")

        val result = sessionManager.connect(serverUrl, auth)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(
            "Expected ServerConnectionException.Unauthorized but was ${exception?.javaClass?.name}: ${exception?.message}",
            exception is ServerConnectionException.Unauthorized
        )
    }

    @Test
    fun connect_failure_connection_refused_returns_unreachable_exception() = runTest {
        // 서버를 시작한 뒤 바로 종료하여 연결 거부 포트 생성
        mockWebServer.start()
        val serverUrl = mockWebServer.url("/").toString()
        mockWebServer.shutdown()

        val auth = AuthMode.Basic("admin", "password")
        val result = sessionManager.connect(serverUrl, auth)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(
            "Expected ServerConnectionException.Unreachable but was ${exception?.javaClass?.name}: ${exception?.message}",
            exception is ServerConnectionException.Unreachable
        )
    }

    @Test
    fun connect_failure_invalid_url_returns_invalid_url_exception() = runTest {
        val invalidUrl = "not-a-valid-url"
        val auth = AuthMode.Basic("admin", "password")

        val result = sessionManager.connect(invalidUrl, auth)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(
            "Expected ServerConnectionException.InvalidUrl but was ${exception?.javaClass?.name}",
            exception is ServerConnectionException.InvalidUrl
        )
    }

    @Test
    fun connect_api_key_auth_sends_x_api_key_header() = runTest {
        mockWebServer.start()
        val userJson = """
            {
                "id": "01HABCDEF1234567",
                "email": "apikey-user@example.com",
                "roles": ["PAGE_STREAMING"],
                "sharedAllLibraries": true,
                "sharedLibrariesIds": [],
                "labelsAllow": [],
                "labelsExclude": []
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(userJson)
        )

        val serverUrl = mockWebServer.url("/").toString()
        val apiKey = "sample-secret-api-key"
        val auth = AuthMode.ApiKey(apiKey)

        val result = sessionManager.connect(serverUrl, auth)

        assertTrue(result.isSuccess)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals(apiKey, recordedRequest.getHeader("X-API-Key"))
        assertEquals("apikey", fakePreferences.authMethodFlow.value)
        assertEquals(apiKey, fakePreferences.authConfigFlow.value.apiKey)
    }
}
