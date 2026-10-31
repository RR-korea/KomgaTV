package com.komgatv.feature.settings

import android.content.Context
import com.komgatv.core.datastore.AuthConfig
import com.komgatv.core.datastore.SavedServer
import com.komgatv.core.datastore.ServerConfig
import com.komgatv.core.datastore.ServerPreferences
import com.komgatv.core.network.ServerSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

private class FakeServerPreferences : ServerPreferences {
    val configFlow = MutableStateFlow(
        ServerConfig(
            serverUrl = "http://initial.example.com",
            authMethod = "basic",
            username = "initialUser",
            password = "initialPassword",
        )
    )
    val urlFlow = MutableStateFlow("http://initial.example.com")
    val authConfigFlow = MutableStateFlow(
        AuthConfig(username = "initialUser", password = "initialPassword")
    )
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

    val savedFlow = MutableStateFlow(
        listOf(
            SavedServer("http://initial.example.com|initialUser", "http://initial.example.com", "basic", "initialUser", true),
            SavedServer("http://other.example.com|kid", "http://other.example.com", "basic", "kid", false),
        ),
    )
    val removed = mutableListOf<String>()

    override val savedServers: Flow<List<SavedServer>> = savedFlow

    override suspend fun switchTo(serverId: String): Boolean {
        val target = savedFlow.value.firstOrNull { it.id == serverId } ?: return false
        urlFlow.value = target.url
        configFlow.value = configFlow.value.copy(serverUrl = target.url, username = target.username)
        savedFlow.value = savedFlow.value.map { it.copy(isActive = it.id == serverId) }
        return true
    }

    override suspend fun removeServer(serverId: String) {
        removed += serverId
        savedFlow.value = savedFlow.value.filterNot { it.id == serverId }
    }
}

private class FakeContext(private val tempDir: File) : android.content.ContextWrapper(null) {
    override fun getCacheDir(): File = tempDir
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakePreferences: FakeServerPreferences
    private lateinit var sessionManager: ServerSessionManager
    private lateinit var fakeContext: Context
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockWebServer = MockWebServer()
        fakePreferences = FakeServerPreferences()
        sessionManager = ServerSessionManager(fakePreferences)

        val tempDir = File(System.getProperty("java.io.tmpdir"), "komgatv_test_cache").apply { mkdirs() }
        fakeContext = FakeContext(tempDir)

        viewModel = SettingsViewModel(
            serverPreferences = fakePreferences,
            sessionManager = sessionManager,
            context = fakeContext,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `저장된 서버로 바꾸면 연결 설정이 바뀌고 홈부터 다시 연다`() = runTest {
        viewModel.switchServer("http://other.example.com|kid")
        advanceUntilIdle()

        assertEquals("http://other.example.com", fakePreferences.urlFlow.value)
        assertTrue(viewModel.uiState.value.serverSwitched)
        assertTrue(viewModel.savedServers.value.single { it.isActive }.id == "http://other.example.com|kid")
    }

    @Test
    fun `없는 서버로는 바꾸지 않는다`() = runTest {
        viewModel.switchServer("nope")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.serverSwitched)
        assertEquals("http://initial.example.com", fakePreferences.urlFlow.value)
    }

    @Test
    fun `저장된 서버를 지우고, 새 서버 추가는 입력칸을 비운다`() = runTest {
        viewModel.removeServer("http://other.example.com|kid")
        advanceUntilIdle()
        assertEquals(listOf("http://other.example.com|kid"), fakePreferences.removed)
        assertEquals(1, viewModel.savedServers.value.size)

        viewModel.startNewServer()
        val state = viewModel.uiState.value
        assertEquals("", state.serverUrl)
        assertEquals("", state.username)
        assertEquals("", state.password)
    }

    @Test
    fun init_loads_stored_settings() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertEquals("http://initial.example.com", state.serverUrl)
        assertEquals("basic", state.authMethod)
        assertEquals("initialUser", state.username)
        assertEquals("initialPassword", state.password)
    }

    @Test
    fun update_fields_reflects_in_ui_state() = runTest(testDispatcher) {
        viewModel.updateServerUrl("http://192.168.1.50:25600")
        viewModel.updateAuthMethod("apikey")
        viewModel.updateApiKey("sample-api-key")

        val state = viewModel.uiState.value
        assertEquals("http://192.168.1.50:25600", state.serverUrl)
        assertEquals("apikey", state.authMethod)
        assertEquals("sample-api-key", state.apiKey)
    }

    @Test
    fun test_connection_with_empty_url_sets_missing_fields_error() = runTest(testDispatcher) {
        viewModel.updateServerUrl("")
        viewModel.testConnection()

        assertEquals(ConnectionResult.Failed.MissingFields, viewModel.uiState.value.connectionResult)
    }

    @Test
    fun save_settings_success_updates_save_success_state() = runTest(testDispatcher) {
        mockWebServer.start()
        val userJson = """
            {
                "id": "01HABCDEF1234567",
                "email": "saved-user@example.com",
                "roles": ["ADMIN"],
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
        viewModel.updateServerUrl(serverUrl)
        viewModel.updateAuthMethod("basic")
        viewModel.updateUsername("admin")
        viewModel.updatePassword("password")

        viewModel.saveSettings()
        val state = viewModel.uiState.first {
            it.connectionResult != ConnectionResult.Testing && it.connectionResult != ConnectionResult.None
        }

        assertEquals(ConnectionResult.Success, state.connectionResult)
        assertTrue(state.saveSuccess)
        assertFalse(state.isSaving)
    }

    @Test
    fun save_settings_401_sets_unauthorized_state() = runTest(testDispatcher) {
        mockWebServer.start()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":401,"error":"Unauthorized"}""")
        )

        val serverUrl = mockWebServer.url("/").toString()
        viewModel.updateServerUrl(serverUrl)
        viewModel.updateAuthMethod("basic")
        viewModel.updateUsername("admin")
        viewModel.updatePassword("wrong")

        viewModel.saveSettings()
        val state = viewModel.uiState.first {
            it.connectionResult != ConnectionResult.Testing && it.connectionResult != ConnectionResult.None
        }

        assertEquals(ConnectionResult.Failed.Unauthorized, state.connectionResult)
        assertFalse(state.saveSuccess)
        assertFalse(state.isSaving)
    }
}
