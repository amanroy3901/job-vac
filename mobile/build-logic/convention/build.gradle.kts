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
 * (android/build.gradle.kts) so all modules share one copy. Android plugin classes are only touched when an
 * Android convention plugin is applied, so the JVM-only build never needs AGP at runtime.
 */
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
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
        register("androidApplication") {
            id = libs.plugins.sm.android.application.get().pluginId
            implementationClass = "com.sarkarimatch.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.sm.android.library.get().pluginId
            implementationClass = "com.sarkarimatch.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.sm.android.compose.get().pluginId
            implementationClass = "com.sarkarimatch.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.sm.android.feature.get().pluginId
            implementationClass = "com.sarkarimatch.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = libs.plugins.sm.android.room.get().pluginId
            implementationClass = "com.sarkarimatch.buildlogic.AndroidRoomConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.sm.hilt.get().pluginId
            implementationClass = "com.sarkarimatch.buildlogic.HiltConventionPlugin"
        }
    }
}
