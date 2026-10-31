package com.komgatv.core.data.error

import com.komgatv.core.data.repository.NotConnectedException
import com.komgatv.core.model.LoadError
import com.komgatv.core.network.isHttpUnauthorized
import java.io.IOException

/**
 * 리포지토리 호출에서 난 예외를 화면용 오류 종류로 바꾼다. 원인(cause) 사슬도 따라간다.
 */
fun Throwable.toLoadError(): LoadError {
    var current: Throwable? = this
    while (current != null) {
        when {
            current is NotConnectedException -> return LoadError.NOT_CONNECTED
            current.isHttpUnauthorized() -> return LoadError.UNAUTHORIZED
            current is HttpStatusException && current.code == 401 -> return LoadError.UNAUTHORIZED
            current is IOException -> return LoadError.UNREACHABLE
        }
        current = current.cause
    }
    return LoadError.UNKNOWN
}
