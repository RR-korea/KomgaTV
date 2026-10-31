// core:network — Komga 서버 세션 (komga-client + Ktor OkHttp 엔진, OkHttpClient를 Coil과 공유)

plugins {
    alias(libs.plugins.komgatv.android.library)
    alias(libs.plugins.komgatv.hilt)
}

android {
    namespace = "com.komgatv.core.network"
}

dependencies {
    implementation(project(":core:datastore"))

    // komga-client 모델(KomgaBook 등)과 kotlinx-datetime 타입이 core:data에 노출된다.
    api(libs.komga.client)
    api(libs.kotlinx.datetime)
    api(libs.okhttp)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
