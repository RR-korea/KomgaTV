plugins {
    `kotlin-dsl`
}

dependencies {
    // implementation으로 두어 컨벤션 플러그인이 적용될 때 같은 버전의 AGP·KGP·KSP·Hilt 플러그인이 쓰이게 한다.
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "komgatv.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "komgatv.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "komgatv.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "komgatv.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = "komgatv.hilt"
            implementationClass = "HiltConventionPlugin"
        }
    }
}
