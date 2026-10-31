// core:model 모듈 빌드 스크립트
// 앱 전체에서 사용되는 데이터 모델 클래스 정의 (최소 의존성)

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.komgatv.core.model"
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
}

dependencies {
    // Kotlinx Serialization - 모델 직렬화/역직렬화
    implementation(libs.kotlinx.serialization.json)
}
