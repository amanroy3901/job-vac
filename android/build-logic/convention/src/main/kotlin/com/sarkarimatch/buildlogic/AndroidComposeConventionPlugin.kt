package com.sarkarimatch.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/** `sm.android.compose`: enables Compose on an application or library module (apply after the Android plugin). */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true
            extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true

            extensions.configure(ComposeCompilerGradlePluginExtension::class.java) {
                // Stability config: kotlinx immutable collections and java.time are stable for us.
                stabilityConfigurationFiles.add(
                    rootProject.layout.projectDirectory.file("compose_compiler_config.conf"),
                )
            }

            dependencies {
                val bom = libs.lib("androidx-compose-bom")
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))
                add("implementation", libs.lib("androidx-compose-ui"))
                add("implementation", libs.lib("androidx-compose-foundation"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            }
        }
    }
}
