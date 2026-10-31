// core:data — 리포지토리 (komga-client 모델 → core:model 매핑)

plugins {
    alias(libs.plugins.komgatv.android.library)
    alias(libs.plugins.komgatv.hilt)
}

android {
    namespace = "com.komgatv.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    implementation(libs.kotlinx.coroutines.android)
}
