package com.sarkarimatch.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `sm.android.application`: the :app module. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
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
            configureKotlinAndroid()
        }
    }
}
