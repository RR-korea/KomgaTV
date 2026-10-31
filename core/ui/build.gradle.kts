// core:ui 모듈 빌드 스크립트
// Android TV용 공통 Compose UI 컴포넌트 (카드, 리스트, 테마 등)

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.komgatv.core.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 모델 모듈 참조
    implementation(project(":core:model"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    debugImplementation(libs.compose.ui.tooling)

    // Android TV
    implementation(libs.tv.foundation)
    implementation(libs.tv.material)

    // Coil 이미지 로딩
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
