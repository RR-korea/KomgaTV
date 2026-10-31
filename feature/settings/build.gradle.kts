// feature:settings — 서버 연결 설정

plugins {
    alias(libs.plugins.komgatv.android.feature)
}

android {
    namespace = "com.komgatv.feature.settings"
}

dependencies {
    implementation(project(":core:datastore"))
}
