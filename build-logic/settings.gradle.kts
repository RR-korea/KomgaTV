// 컨벤션 플러그인 빌드 — 모든 모듈의 공통 Android/Compose/Hilt 설정을 한 곳에 둔다.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
