package com.komgatv.core.network

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

/** komga-client 호출이 401로 거절됐는지 (원인 사슬은 따라가지 않는다) */
fun Throwable.isHttpUnauthorized(): Boolean =
    this is ResponseException && response.status == HttpStatusCode.Unauthorized
