// core:datastore — 서버 설정·자격 증명 및 리더 보기 설정 저장

plugins {
    alias(libs.plugins.komgatv.android.library)
    alias(libs.plugins.komgatv.hilt)
}

android {
    namespace = "com.komgatv.core.datastore"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
