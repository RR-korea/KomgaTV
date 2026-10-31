package com.komgatv.core.network

/**
 * Komga 서버 연결 실패 원인 분류
 */
sealed class ServerConnectionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * 주소 오류: 잘못된 URL 형식, 지원하지 않는 스킴, 호스트 주소 변환 실패(DNS 오류)
     */
    class InvalidUrl(
        message: String = "서버 주소 형식이 올바르지 않습니다.",
        cause: Throwable? = null,
    ) : ServerConnectionException(message, cause)

    /**
     * 연결 불가: 서버 연결 거부(Connection refused), 네트워크 연결 끊김, 타임아웃
     */
    class Unreachable(
        message: String = "서버에 연결할 수 없습니다.",
        cause: Throwable? = null,
    ) : ServerConnectionException(message, cause)

    /**
     * 401 인증 실패: 계정 정보(아이디/비밀번호) 또는 API 키가 일치하지 않음
     */
    class Unauthorized(
        message: String = "인증에 실패했습니다 (401).",
        cause: Throwable? = null,
    ) : ServerConnectionException(message, cause)

    /**
     * SSL/TLS 인증서 오류: 자체 서명 인증서이거나 신뢰할 수 없는 CA (현재 미지원)
     */
    class SslError(
        message: String = "자체 서명 또는 신뢰할 수 없는 SSL 인증서입니다. (현재 미지원)",
        cause: Throwable? = null,
    ) : ServerConnectionException(message, cause)

    /**
     * 기타 오류: 500 등 서버 내부 오류, 알 수 없는 예외
     */
    class Other(
        message: String = "서버 연결 중 오류가 발생했습니다.",
        cause: Throwable? = null,
    ) : ServerConnectionException(message, cause)
}
