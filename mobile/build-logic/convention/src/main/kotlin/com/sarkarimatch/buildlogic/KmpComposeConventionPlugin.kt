package com.sarkarimatch.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `sm.kmp.compose`: Compose Multiplatform on top of `sm.kmp.android.library`. Adds the JetBrains Compose runtime,
 * foundation, ui, Material 3 and Compose resources (`src/commonMain/composeResources`: strings, fonts, vector
 * drawables) to `commonMain`, and enables Android resources so Compose resources are packaged on Android.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_compiler_config.conf"))
            }
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    androidResources.enable = true
                }
                sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.lib("compose-runtime"))
                    implementation(libs.lib("compose-foundation"))
                    implementation(libs.lib("compose-ui"))
                    implementation(libs.lib("compose-material3"))
                    implementation(libs.lib("compose-components-resources"))
                    implementation(libs.lib("compose-ui-tooling-preview"))
                }
            }
        }
    }
}
