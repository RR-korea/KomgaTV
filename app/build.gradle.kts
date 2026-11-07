// KomgaTV 앱 모듈 — 진입점, 내비게이션, Coil 이미지 로더

plugins {
    alias(libs.plugins.komgatv.android.application)
    alias(libs.plugins.komgatv.android.compose)
    alias(libs.plugins.komgatv.hilt)
}

// 버전 — APK 파일 이름에도 들어간다 (KomgaTV-v0.2.9-b11-debug.apk)
val appVersionCode = 26
val appVersionName = "1.0.11"

base {
    archivesName = "TVReaderForKomga-v$appVersionName-b$appVersionCode"
}

android {
    namespace = "com.komgatv.app"

    defaultConfig {
        applicationId = "com.komgatv.app"
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // 업로드 키 — 비밀번호는 저장소에 두지 않고 환경 변수로만 받는다 (tools/build-release.ps1)
    val uploadStore = System.getenv("KOMGATV_UPLOAD_STORE_FILE")
    val uploadPassword = System.getenv("KOMGATV_UPLOAD_STORE_PASSWORD")
    val uploadSigning = if (uploadStore != null && uploadPassword != null) {
        signingConfigs.create("upload") {
            storeFile = file(uploadStore)
            storePassword = uploadPassword
            keyAlias = System.getenv("KOMGATV_UPLOAD_KEY_ALIAS") ?: "upload"
            keyPassword = System.getenv("KOMGATV_UPLOAD_KEY_PASSWORD") ?: uploadPassword
        }
    } else {
        null
    }

    buildTypes {
        release {
            signingConfig = uploadSigning
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
