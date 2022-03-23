import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.configurationcache.extensions.*
import pl.mareklangiewicz.defaults.*
import pl.mareklangiewicz.sourcefun.*
import pl.mareklangiewicz.utils.*

plugins {
    id("com.android.application") version vers.androidGradlePlugin
    kotlin("android") version vers.kotlin
}

repositories { defaultRepos() }

val generateVersionDetails by tasks.registering(VersionDetailsTask::class) {
    generatedAssetsDir provides layout.buildDirectory.dir("generated/assets")
}

android {
    defaultAndro("pl.mareklangiewicz.playgrounds", withCompose = true)
    sourceSets["main"].assets.srcDir(generateVersionDetails)
}

dependencies {
    implementation(project(":lib1"))
    implementation(project(":lib-ui-samples"))
    defaultAndroDeps(withCompose = true)
    defaultAndroTestDeps(configuration = "implementation", withCompose = true)
    // I use test stuff in main sources so I can add some tests sources to playgrounds app
}

group = "pl.mareklangiewicz.playgrounds"
version = "0.0.01"

tasks.defaultKotlinCompileOptions()

androidComponents {
    onVariants {
        val capitalizedVariantName = it.name.capitalized()
        afterEvaluate {
            // FIXME_someday: watch: https://issuetracker.google.com/issues/191774971
            tasks.named("generate${capitalizedVariantName}Assets") { dependsOn("generateVersionDetails") }
        }
    }
}

// region Kotlin Module Build Template

fun TaskCollection<Task>.defaultKotlinCompileOptions(
    jvmTargetVer: String = vers.defaultJvm,
    requiresOptIn: Boolean = true
) = withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = jvmTargetVer
        if (requiresOptIn) freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
    }
}

// endregion Kotlin Module Build Template

// region Andro Module Build Template

fun ApplicationExtension.defaultAndro(
    appId: String,
    appVerCode: Int = 1,
    appVerName: String = v(patch = appVerCode),
    jvmVersion: String = vers.defaultJvm,
    withCompose: Boolean = false,
) {
    compileSdk = vers.androidCompileSdk
    defaultCompileOptions(jvmVersion)
    defaultDefaultConfig(appId, appVerCode, appVerName)
    defaultBuildTypes()
    if (withCompose) defaultComposeStuff()
    defaultPackagingOptions()
}

fun LibraryExtension.defaultAndro(
    jvmVersion: String = vers.defaultJvm,
    withCompose: Boolean = false,
) {
    compileSdk = vers.androidCompileSdk
    defaultCompileOptions(jvmVersion)
    defaultDefaultConfig()
    defaultBuildTypes()
    if (withCompose) defaultComposeStuff()
    defaultPackagingOptions()
}

fun ApplicationExtension.defaultDefaultConfig(
    appId: String,
    appVerCode: Int = 1,
    appVerName: String = v(patch = appVerCode)
) = defaultConfig {
    applicationId = appId
    minSdk = vers.androidMinSdk
    targetSdk = vers.androidTargetSdk
    versionCode = appVerCode
    versionName = appVerName
    testInstrumentationRunner = vers.androidTestRunnerClass
}

fun LibraryExtension.defaultDefaultConfig() = defaultConfig {
    minSdk = vers.androidMinSdk
    targetSdk = vers.androidTargetSdk
    testInstrumentationRunner = vers.androidTestRunnerClass
}

fun CommonExtension<*,*,*,*>.defaultCompileOptions(
    jvmVersion: String = vers.defaultJvm
) = compileOptions {
    sourceCompatibility(jvmVersion)
    targetCompatibility(jvmVersion)
}

fun ApplicationExtension.defaultBuildTypes() = buildTypes { release { isMinifyEnabled = false } }
fun LibraryExtension.defaultBuildTypes() = buildTypes { release { isMinifyEnabled = false } }

fun CommonExtension<*,*,*,*>.defaultComposeStuff() {
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = vers.composeAndroidCompiler
    }
}

fun CommonExtension<*,*,*,*>.defaultPackagingOptions() = packagingOptions {
    resources.excludes.defaultAndroExcludedResources()
}

// endregion Andro Module Build Template