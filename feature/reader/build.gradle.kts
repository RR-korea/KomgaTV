// feature:reader — 만화 리더

plugins {
    alias(libs.plugins.komgatv.android.feature)
}

android {
    namespace = "com.komgatv.feature.reader"
}

dependencies {
    implementation(libs.coil.compose)
}
