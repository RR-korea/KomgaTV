// KomgaTV - Android TV 만화/웹툰 뷰어 앱
// 프로젝트 설정 파일

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolution {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KomgaTV"

// 앱 모듈
include(":app")

// 코어 모듈 - 공통 기능 계층
include(":core:network")    // 네트워크 통신 (Retrofit, OkHttp)
include(":core:model")      // 데이터 모델 클래스
include(":core:data")       // 데이터 저장소 및 리포지토리
include(":core:ui")         // 공통 UI 컴포넌트 (Compose TV)

// 피처 모듈 - 화면별 기능
include(":feature:home")      // 홈 화면
include(":feature:library")   // 라이브러리 탐색
include(":feature:reader")    // 만화 리더
include(":feature:search")    // 검색 기능
include(":feature:settings")  // 설정 화면
