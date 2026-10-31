package com.komgatv.core.network

import com.komgatv.core.datastore.ServerConfig
import com.komgatv.core.datastore.ServerPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import snd.komga.client.KomgaClientFactory
import snd.komga.client.user.KomgaUser
import timber.log.Timber
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

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
class ServerSessionManager @Inject constructor(
    private val serverPreferences: ServerPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _session = MutableStateFlow<KomgaSession?>(null)

    /** 현재 활성화된 세션의 StateFlow (미연결 시 null) */
    val session: StateFlow<KomgaSession?> = _session.asStateFlow()

    init {
        scope.launch {
            serverPreferences.serverConfig.collect { config ->
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
     * @return 연결 성공 시 KomgaUser, 실패 시 ServerConnectionException을 담은 Result.failure
     */
    suspend fun connect(url: String, auth: AuthMode): Result<KomgaUser> {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return Result.failure(ServerConnectionException.InvalidUrl("서버 주소를 입력해주세요."))
        }

        val httpUrl = trimmedUrl.toHttpUrlOrNull()
        if (httpUrl == null || (httpUrl.scheme != "http" && httpUrl.scheme != "https")) {
            return Result.failure(ServerConnectionException.InvalidUrl())
        }

        val cleanUrl = trimmedUrl.trimEnd('/')

        return try {
            val tempSession = buildSession(cleanUrl, auth)
            val user = tempSession.komgaClient.userClient().getMe()

            serverPreferences.saveServerUrl(cleanUrl)
            when (auth) {
                is AuthMode.Basic -> serverPreferences.saveBasicAuth(auth.username, auth.password)
                is AuthMode.ApiKey -> serverPreferences.saveApiKey(auth.apiKey)
            }

            _session.value = tempSession
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Komga 서버 연결 실패: %s", cleanUrl)
            Result.failure(classifyException(e))
        }
    }

    /**
     * 연결 시도 중 발생한 예외를 원인별 ServerConnectionException으로 분류합니다.
     */
    fun classifyException(throwable: Throwable): ServerConnectionException {
        var current: Throwable? = throwable
        while (current != null) {
            when {
                // 401 Unauthorized 검사
                current is ResponseException && current.response.status == HttpStatusCode.Unauthorized -> {
                    return ServerConnectionException.Unauthorized(cause = throwable)
                }
                // SSL / 자체 서명 인증서 관련 오류
                current is SSLException ||
                    current is SSLHandshakeException ||
                    current is SSLPeerUnverifiedException ||
                    current is CertificateException -> {
                    return ServerConnectionException.SslError(cause = throwable)
                }
                // 주소 / DNS 오류
                current is UnknownHostException ||
                    current is MalformedURLException -> {
                    return ServerConnectionException.InvalidUrl(cause = throwable)
                }
                // 연결 불가 / 타임아웃 / 연결 거부
                current is ConnectException ||
                    current is SocketTimeoutException ||
                    current is SocketException ||
                    current is InterruptedIOException -> {
                    return ServerConnectionException.Unreachable(cause = throwable)
                }
            }
            current = current.cause
        }

        return ServerConnectionException.Other(
            message = throwable.message ?: "알 수 없는 오류가 발생했습니다.",
            cause = throwable,
        )
    }

    /**
     * 세션을 종료하고 저장된 설정을 초기화합니다.
     */
    suspend fun disconnect() {
        serverPreferences.clearAll()
        _session.value = null
    }
}
