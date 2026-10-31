// feature:reader — 만화 리더

plugins {
    alias(libs.plugins.komgatv.android.feature)
}

android {
    namespace = "com.komgatv.feature.reader"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
