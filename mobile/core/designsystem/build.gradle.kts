// Marigold Home design system (docs/design/02-design-system.md, tokens.json v2.0) for Android and iOS.
plugins {
    alias(libs.plugins.sm.kmp.android.library)
    alias(libs.plugins.sm.kmp.compose)
}

/*
 * TokensParityTest (commonTest) compares the Kotlin theme with docs/design/tokens.json. commonTest cannot read files
 * on iOS, so the JSON is embedded as a Kotlin string constant at build time.
 */
val generateTokensSource = tasks.register("generateTokensSource") {
    val tokens = rootProject.layout.projectDirectory.file("../docs/design/tokens.json")
    val output = layout.buildDirectory.dir("generated/tokens/kotlin")
    inputs.file(tokens).withPathSensitivity(PathSensitivity.NAME_ONLY)
    outputs.dir(output)
    doLast {
        val dir = output.get().asFile.resolve("com/sarkarimatch/core/designsystem/theme").apply { mkdirs() }
        dir.resolve("TokensJson.kt").writeText(
            "// Generated from docs/design/tokens.json by :core:designsystem:generateTokensSource. Do not edit.\n" +
                "package com.sarkarimatch.core.designsystem.theme\n\n" +
                "internal const val TOKENS_JSON: String = \$\$\$\"\"\"" + tokens.asFile.readText() + "\"\"\"\n",
        )
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.compose.material3)
            api(libs.compose.components.resources)
            implementation(libs.kotlinx.collections.immutable)
        }
        commonTest {
            kotlin.srcDir(generateTokensSource)
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.sarkarimatch.core.designsystem.resources"
    generateResClass = always
}
