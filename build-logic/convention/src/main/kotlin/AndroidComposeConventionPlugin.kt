import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Compose 컴파일러 + Compose BOM + TV Material. application/library 플러그인 다음에 적용한다. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true
        dependencies {
            val bom = platform(libs.findLibrary("compose-bom").get())
            add("implementation", bom)
            add("implementation", libs.findLibrary("compose-ui").get())
            add("implementation", libs.findLibrary("compose-ui-graphics").get())
            add("implementation", libs.findLibrary("compose-foundation").get())
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("implementation", libs.findLibrary("tv-material").get())
            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
        }
    }
}
