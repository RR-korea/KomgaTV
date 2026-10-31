package com.komgatv.core.network

import com.komgatv.core.datastore.ServerConfig
import com.komgatv.core.datastore.ServerPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import snd.komga.client.KomgaClientFactory
import snd.komga.client.user.KomgaUser
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 인증 방식 구분
 */
sealed interface AuthMode {
    data class Basic(val username: String, val password: String) : AuthMode
    data class ApiKey(val apiKey: String) : AuthMode
}

/**
 * 활성화된 Komga 서버 세션
 *
 * @property okHttpClient 인증 인터셉터가 적용된 OkHttpClient (Coil 이미지 요청에도 공유)
 * @property komgaClient Komga API 호출용 클라이언트 팩토리
 * @property baseUrl Komga 서버의 기본 URL (끝 슬래시 제외)
 */
data class KomgaSession(
    val okHttpClient: OkHttpClient,
    val komgaClient: KomgaClientFactory,
    val baseUrl: String,
)

/**
 * Komga 서버 세션 단일 소유자
 *
 * ServerPreferences를 관찰하여 설정 변경 시 세션을 자동으로 재구성합니다.
 * 요청 경로에서 동기 블로킹(runBlocking) 없이 StateFlow로 세션을 제공합니다.
 */
@Singleton
class ServerSessionManager internal constructor(
    private val serverPreferences: ServerPreferences?,
    testBaseUrl: String?,
) {
    @Inject
    constructor(serverPreferences: ServerPreferences) : this(serverPreferences, null)

    /** 테스트용 보조 생성자 */
    constructor(baseUrl: String) : this(null, baseUrl)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _session = MutableStateFlow<KomgaSession?>(null)

    /** 현재 활성화된 세션의 StateFlow (미연결 시 null) */
    val session: StateFlow<KomgaSession?> = _session.asStateFlow()

    init {
        if (!testBaseUrl.isNullOrBlank()) {
            val cleanUrl = testBaseUrl.trimEnd('/')
            _session.value = KomgaSession(
                okHttpClient = OkHttpClient(),
                komgaClient = KomgaClientFactory.Builder().baseUrl { cleanUrl }.build(),
                baseUrl = cleanUrl,
            )
        }

        serverPreferences?.let { prefs ->
            scope.launch {
                prefs.serverConfig.collect { config ->
                    try {
                        if (config.isConfigured) {
                            val auth = if (config.authMethod == "apikey") {
                                AuthMode.ApiKey(config.apiKey)
                            } else {
                                AuthMode.Basic(config.username, config.password)
                            }
                            _session.value = buildSession(config.serverUrl, auth)
                            Timber.d("Komga session initialized for: %s", config.serverUrl)
                        } else {
                            _session.value = null
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to initialize Komga session")
                        _session.value = null
                    }
                }
            }
        }
    }

    /**
     * 지정한 서버 URL과 인증 정보로 세션을 생성합니다.
     */
    fun buildSession(url: String, auth: AuthMode): KomgaSession {
        val cleanUrl = url.trimEnd('/')

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()

            when (auth) {
                is AuthMode.Basic -> {
                    val credential = Credentials.basic(auth.username, auth.password)
                    builder.header("Authorization", credential)
                }
                is AuthMode.ApiKey -> {
                    builder.header("X-API-Key", auth.apiKey)
                }
            }

            chain.proceed(builder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Timber.tag("KomgaHttp").d(message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val ktorClient = HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
        }

        // 인증 헤더는 OkHttp 인터셉터가 붙인다 (Coil 이미지 요청과 같은 경로).
        val komgaClient = KomgaClientFactory.Builder()
            .baseUrl { cleanUrl }
            .ktor(ktorClient)
            .build()

        return KomgaSession(
            okHttpClient = okHttpClient,
            komgaClient = komgaClient,
            baseUrl = cleanUrl,
        )
    }

    /**
     * 서버에 연결을 시도하고 유효성을 검증한 뒤 성공 시 저장합니다.
     *
     * @param url Komga 서버 URL
     * @param auth 인증 정보 (Basic 또는 ApiKey)
     * @return 연결 성공 시 KomgaUser, 실패 시 Result.failure
     */
    suspend fun connect(url: String, auth: AuthMode): Result<KomgaUser> = runCatching {
        val tempSession = buildSession(url, auth)
        val user = tempSession.komgaClient.userClient().getMe()

        serverPreferences?.saveServerUrl(url)
        when (auth) {
            is AuthMode.Basic -> serverPreferences?.saveBasicAuth(auth.username, auth.password)
            is AuthMode.ApiKey -> serverPreferences?.saveApiKey(auth.apiKey)
        }

        _session.value = tempSession
        user
    }

    /**
     * 세션을 종료하고 저장된 설정을 초기화합니다.
     */
    suspend fun disconnect() {
        serverPreferences?.clearAll()
        _session.value = null
    }
}
