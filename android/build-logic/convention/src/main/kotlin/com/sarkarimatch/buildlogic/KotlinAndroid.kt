package com.sarkarimatch.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * AGP 9 compiles Kotlin itself (built-in Kotlin), so no `org.jetbrains.kotlin.android` plugin is applied.
 * We only pin the JVM target and opt-ins on every Kotlin compile task.
 */
internal fun Project.configureKotlinAndroid() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }
    configureDetekt()
}
