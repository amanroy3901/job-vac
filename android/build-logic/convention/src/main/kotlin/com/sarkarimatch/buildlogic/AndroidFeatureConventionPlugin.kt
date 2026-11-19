package com.sarkarimatch.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `sm.android.feature`: library + Compose + Hilt + serialization (type-safe routes) + the deps every feature
 * needs. Features never depend on each other (docs/frontend/navigation.md §1).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("sm.android.library")
            pluginManager.apply("sm.android.compose")
            pluginManager.apply("sm.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:data"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-navigation-compose"))
                add("implementation", libs.lib("androidx-hilt-navigation-compose"))
                add("implementation", libs.lib("kotlinx-serialization-json"))
                add("implementation", libs.lib("kotlinx-collections-immutable"))
                add("testImplementation", project(":core:testing"))
                add("testImplementation", libs.lib("turbine"))
            }
        }
    }
}
