import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** 화면 모듈 공통: library + compose + hilt + core:ui/data/model + 내비게이션·라이프사이클. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("komgatv.android.library")
        pluginManager.apply("komgatv.android.compose")
        pluginManager.apply("komgatv.hilt")
        dependencies {
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:model"))
            add("implementation", libs.findLibrary("hilt-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("navigation-compose").get())
            add("implementation", libs.findLibrary("lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("timber").get())
        }
    }
}
