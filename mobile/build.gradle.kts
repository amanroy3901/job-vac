/*
 * Root build. Third-party Gradle plugins are put on the ROOT classpath here, once, so that every module
 * (and every convention plugin in build-logic, which only has them as compileOnly) shares one copy of
 * the Kotlin Gradle plugin, AGP and the Compose Multiplatform plugin. Android plugins are only added when the
 * Android modules are part of the build (see settings.gradle.kts), so the pure KMP modules still build where
 * Google Maven is unreachable.
 */
buildscript {
    dependencies {
        if (rootProject.findProject(":androidApp") != null) {
            classpath(libs.android.gradlePlugin)
            classpath(libs.ksp.gradlePlugin)
            classpath(libs.room.gradlePlugin)
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.detekt) apply false
}
