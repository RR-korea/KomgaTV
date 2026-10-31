import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

// SDK 버전은 여기 한 곳에서만 정한다 (LEAD_REVIEW D7).
// compileSdk 37: komga-client 0.11.0 AAR이 minCompileSdk=37을 요구한다. kimchi SDK에는 android-37.2가 있다.
internal const val COMPILE_SDK = 37
internal const val COMPILE_SDK_MINOR = 2
internal const val TARGET_SDK = 36
internal const val MIN_SDK = 30

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk {
            version = release(COMPILE_SDK) {
                minorApiLevel = COMPILE_SDK_MINOR
            }
        }
        defaultConfig.minSdk = MIN_SDK
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}
