package com.sarkarimatch.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `sm.android.library`: any Android library module (core and feature). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                compileSdk = SmSdk.COMPILE
                defaultConfig {
                    minSdk = SmSdk.MIN
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles("consumer-rules.pro")
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                testOptions.unitTests.isIncludeAndroidResources = true
                // Feature/core modules: resource prefixes are not enforced; strings live per module.
                lint.error += listOf("RtlHardcoded", "RtlSymmetry")
            }
            configureKotlinAndroid()
            dependencies {
                add("testImplementation", libs.lib("junit4"))
                add("testImplementation", libs.lib("kotlin-test"))
                add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            }
        }
    }
}
