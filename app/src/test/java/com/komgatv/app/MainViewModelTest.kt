package com.komgatv.app

import com.komgatv.app.navigation.Screen
import com.komgatv.core.datastore.AuthConfig
import com.komgatv.core.datastore.ServerConfig
import com.komgatv.core.datastore.SavedServer
import com.komgatv.core.datastore.ServerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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
        urlFlow.value = url
        configFlow.value = configFlow.value.copy(serverUrl = url)
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakePreferences: FakeServerPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePreferences = FakeServerPreferences()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun unconfigured_server_starts_at_settings() = runTest(testDispatcher) {
        val viewModel = MainViewModel(fakePreferences)
        val readyState = viewModel.uiState.first { it is MainUiState.Ready } as MainUiState.Ready
        assertEquals(Screen.Settings.route, readyState.startDestination)
    }

    @Test
    fun configured_server_starts_at_home() = runTest(testDispatcher) {
        fakePreferences.saveServerUrl("http://192.168.1.100:25600")
        fakePreferences.saveBasicAuth("user@example.com", "password")
        val viewModel = MainViewModel(fakePreferences)

        val readyState = viewModel.uiState.first { it is MainUiState.Ready } as MainUiState.Ready

        assertEquals(Screen.Home.route, readyState.startDestination)
    }

    @Test
    fun start_destination_does_not_change_after_connecting() = runTest(testDispatcher) {
        val viewModel = MainViewModel(fakePreferences)
        viewModel.uiState.first { it is MainUiState.Ready }

        // 실행 중 연결에 성공해도 시작 화면을 다시 정하지 않는다 (내비게이션 그래프 재생성 방지).
        fakePreferences.saveServerUrl("http://192.168.1.100:25600")
        fakePreferences.saveBasicAuth("user@example.com", "password")

        assertEquals(MainUiState.Ready(Screen.Settings.route), viewModel.uiState.value)
    }
}
