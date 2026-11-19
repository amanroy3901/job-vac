package com.sarkarimatch.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** The `libs` version catalog (gradle/libs.versions.toml), usable from convention plugins. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

fun VersionCatalog.pluginId(alias: String): String = findPlugin(alias).get().get().pluginId

/** Shared SDK levels for every Android target. */
object SmSdk {
    const val COMPILE = 37 // current AndroidX (Compose 1.12, core 1.19) requires compileSdk 37
    const val TARGET = 36
    const val MIN = 26
}

/**
 * Apple targets. iosX64 (Intel simulators) is intentionally absent: Compose Multiplatform 1.12 and JetBrains
 * Navigation 2.10 no longer publish it. Kotlin/Native for Apple targets only compiles on macOS; on Linux/Windows
 * these targets are disabled (gradle.properties: kotlin.native.ignoreDisabledTargets=true).
 */
object SmApple {
    const val FRAMEWORK = "ComposeApp"
}
