package com.sarkarimatch.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * `sm.android.application`: the :androidApp module (MainActivity, Application, manifest, launcher resources).
 * AGP 9 does not allow `com.android.application` in a KMP module, so the shared UI lives in the KMP library
 * :composeApp and this thin module packages it (the current JetBrains KMP wizard layout). AGP 9 compiles Kotlin
 * itself (built-in Kotlin); only the Compose compiler plugin is added.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<ApplicationExtension> {
                compileSdk = SmSdk.COMPILE
                defaultConfig {
                    minSdk = SmSdk.MIN
                    targetSdk = SmSdk.TARGET
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    vectorDrawables.useSupportLibrary = false
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                testOptions.unitTests.isIncludeAndroidResources = true
                packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
                lint {
                    checkDependencies = true
                    abortOnError = true
                    warningsAsErrors = false
                    error += listOf("RtlHardcoded", "RtlSymmetry")
                }
            }
            tasks.withType<KotlinJvmCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                    optIn.add("kotlin.RequiresOptIn")
                }
            }
            configureDetekt()
        }
    }
}
