// KomgaTV - Android TV용 Komga 클라이언트

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KomgaTV"

include(":app")

// 의존 방향: datastore ← network ← data ← feature:* (LEAD_REVIEW D2)
include(":core:model")      // 도메인 모델
include(":core:datastore")  // 서버 설정·자격 증명 저장
include(":core:network")    // Komga 세션 (komga-client + OkHttp)
include(":core:data")       // 리포지토리
include(":core:ui")         // 공통 TV 컴포넌트

include(":feature:home")
include(":feature:library")
include(":feature:reader")
include(":feature:search")
include(":feature:settings")
