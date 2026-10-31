package com.komgatv.core.data.error

import java.io.IOException

/** komga-client를 거치지 않는 직접 요청이 성공이 아닌 HTTP 응답을 받음 */
class HttpStatusException(val code: Int) : IOException("HTTP $code")
