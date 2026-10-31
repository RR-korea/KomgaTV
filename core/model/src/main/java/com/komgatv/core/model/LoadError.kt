package com.komgatv.core.model

/**
 * 데이터를 불러오지 못한 이유 (화면이 언어에 맞는 문구로 바꿔 보여준다)
 */
enum class LoadError {
    /** 서버 설정이 없거나 세션이 없음 */
    NOT_CONNECTED,

    /** 네트워크로 서버에 닿지 못함 (연결 거부, 시간 초과, DNS 등) */
    UNREACHABLE,

    /** 서버가 인증을 거절함 (401) */
    UNAUTHORIZED,

    /** 그 밖의 오류 */
    UNKNOWN,
}
