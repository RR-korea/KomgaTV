// feature:reader — 만화 리더

plugins {
    alias(libs.plugins.komgatv.android.feature)
}

android {
    namespace = "com.komgatv.feature.reader"
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
}
