package com.komgatv.core.datastore

import java.util.Base64

/**
 * 저장된 서버 (화면에 보이는 정보만 — 비밀번호·API Key는 담지 않는다)
 *
 * @property isActive 지금 연결된 서버인지
 */
data class SavedServer(
    val id: String,
    val url: String,
    val authMethod: String,
    val username: String,
    val isActive: Boolean,
)

/** 저장소 안의 서버 한 개. [secret]은 Keystore로 암호화된 비밀번호 또는 API Key. */
internal data class StoredServer(
    val url: String,
    val authMethod: String,
    val username: String,
    val secret: String,
) {
    val id: String get() = serverId(url, authMethod, username)
}

/** 같은 서버·같은 계정이면 같은 ID (API Key는 서버당 하나) */
internal fun serverId(url: String, authMethod: String, username: String): String =
    if (authMethod == "apikey") "$url|apikey" else "$url|$username"

/**
 * 서버 목록 ↔ 문자열. 줄마다 서버 하나, 칸은 ':'로 나누고 각 칸은 Base64(URL-safe)라 어떤 글자도 안전하다.
 */
internal object SavedServerCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(servers: List<StoredServer>): String = servers.joinToString("\n") { server ->
        listOf(server.url, server.authMethod, server.username, server.secret)
            .joinToString(":") { encoder.encodeToString(it.toByteArray(Charsets.UTF_8)) }
    }

    /** 깨진 줄은 건너뛴다 */
    fun decode(text: String?): List<StoredServer> = text.orEmpty().lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(":")
            if (parts.size != 4) return@mapNotNull null
            runCatching {
                val (url, method, user, secret) = parts.map { String(decoder.decode(it), Charsets.UTF_8) }
                StoredServer(url, method, user, secret)
            }.getOrNull()
        }
        .toList()

    /** 같은 ID가 있으면 바꾸고 없으면 맨 뒤에 붙인다 */
    fun upsert(servers: List<StoredServer>, server: StoredServer): List<StoredServer> =
        if (servers.any { it.id == server.id }) servers.map { if (it.id == server.id) server else it } else servers + server
}
