// KomgaTV 앱 모듈 — 진입점, 내비게이션, Coil 이미지 로더

plugins {
    alias(libs.plugins.komgatv.android.application)
    alias(libs.plugins.komgatv.android.compose)
    alias(libs.plugins.komgatv.hilt)
}

// 버전 — APK 파일 이름에도 들어간다 (KomgaTV-v0.2.9-b11-debug.apk)
val appVersionCode = 16
val appVersionName = "1.0.1"

base {
    archivesName = "KomgaTV-v$appVersionName-b$appVersionCode"
}

android {
    namespace = "com.komgatv.app"

    defaultConfig {
        applicationId = "com.komgatv.app"
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:ui"))

    implementation(project(":feature:home"))
    implementation(project(":feature:library"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
