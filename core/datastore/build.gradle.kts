// core:datastore — 서버 설정·자격 증명 저장 (다른 core 모듈에 의존하지 않음)

plugins {
    alias(libs.plugins.komgatv.android.library)
    alias(libs.plugins.komgatv.hilt)
}

android {
    namespace = "com.komgatv.core.datastore"
}

dependencies {
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
