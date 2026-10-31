// feature:home — 홈 화면

plugins {
    alias(libs.plugins.komgatv.android.feature)
}

android {
    namespace = "com.komgatv.feature.home"
}

dependencies {
    implementation(libs.coil.compose)
}
