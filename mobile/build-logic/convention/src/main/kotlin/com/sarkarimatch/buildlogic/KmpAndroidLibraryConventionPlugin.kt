package com.sarkarimatch.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `sm.kmp.android.library`: KMP module with an Android target (AGP 9 `com.android.kotlin.multiplatform.library`,
 * the only Android plugin AGP 9 supports together with KMP) plus `iosArm64` / `iosSimulatorArm64`.
 * `commonTest` runs on the JVM as the Android host test: `./gradlew :<module>:testAndroidHostTest`.
 * Namespace: `com.sarkarimatch.` + the project path (`:core:data` → `com.sarkarimatch.core.data`).
 */
class KmpAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    namespace = "com.sarkarimatch." + path.removePrefix(":").replace(':', '.').lowercase()
                    compileSdk = SmSdk.COMPILE
                    minSdk = SmSdk.MIN
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                    withHostTest {
                        isIncludeAndroidResources = true
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
