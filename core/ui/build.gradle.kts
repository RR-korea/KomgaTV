// core:ui — 공통 TV 컴포넌트

plugins {
    alias(libs.plugins.komgatv.android.library)
    alias(libs.plugins.komgatv.android.compose)
}

android {
    namespace = "com.komgatv.core.ui"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coil.compose)
    implementation(libs.androidx.activity.compose)
}
