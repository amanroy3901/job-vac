package com.sarkarimatch.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `sm.kmp.library`: pure Kotlin Multiplatform module without Android or Compose (:core:model, :core:eligibility).
 * Targets: `jvm` (consumed by Android — KGP lets an Android consumer use the JVM variant — and used for fast unit
 * tests without the Android SDK), `iosArm64`, `iosSimulatorArm64`. Tests live in `commonTest` and run with
 * `./gradlew :core:eligibility:jvmTest` (Linux/macOS) and `iosSimulatorArm64Test` (macOS only).
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            extensions.configure<KotlinMultiplatformExtension> {
                jvm {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                        freeCompilerArgs.add("-Xjdk-release=17")
                    }
                }
                iosArm64()
                iosSimulatorArm64()
                applyCommonKotlinOptions()
                sourceSets.getByName("commonTest").dependencies {
                    implementation(libs.lib("kotlin-test"))
                }
            }
            tasks.withType<Test>().configureEach { useJUnit() }
            configureDetekt()
        }
    }
}

/** Compiler options shared by every KMP module. */
fun KotlinMultiplatformExtension.applyCommonKotlinOptions() {
    compilerOptions {
        optIn.add("kotlin.RequiresOptIn")
        // expect/actual classes (Room's RoomDatabaseConstructor, platform services) are still Beta.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
