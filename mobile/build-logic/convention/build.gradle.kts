import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.sarkarimatch.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

/*
 * compileOnly: the plugins themselves are put on the root project's buildscript classpath
 * (mobile/build.gradle.kts) so all modules share one copy. Android plugin classes are only touched when an
 * Android convention plugin is applied, so the JVM-only build never needs AGP at runtime.
 */
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    implementation(project(":jvm"))
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("kmpAndroidLibrary") {
            id = libs.plugins.sm.kmp.android.library.get().pluginId
            implementationClass = "com.sarkarimatch.buildlogic.KmpAndroidLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = libs.plugins.sm.kmp.compose.get().pluginId
            implementationClass = "com.sarkarimatch.buildlogic.KmpComposeConventionPlugin"
        }
        register("androidApplication") {
            id = libs.plugins.sm.android.application.get().pluginId
            implementationClass = "com.sarkarimatch.buildlogic.AndroidApplicationConventionPlugin"
        }
    }
}
