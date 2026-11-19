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

/** Shared SDK levels for every Android module. */
object SmSdk {
    const val COMPILE = 36
    const val TARGET = 36
    const val MIN = 26
}
