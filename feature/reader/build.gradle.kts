// feature:reader — 만화 리더

plugins {
    alias(libs.plugins.komgatv.android.feature)
}

android {
    namespace = "com.komgatv.feature.reader"

    defaultConfig {
        // 번역은 ML Kit(실제 기기·에뮬레이터)가 있어야 해서 기기 테스트로 확인한다
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    // 만화 번역 — TV 안에서 글자 인식(일본어 모델이 영어도 읽는다)과 번역
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.translate)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
