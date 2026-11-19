// On-device eligibility evaluator (docs/backend/matching-engine.md). Pure Kotlin Multiplatform, no I/O: the same
// rules run on Android and iOS; later this is replaced by / merged into the shared :matching-core.
plugins {
    alias(libs.plugins.sm.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * The worked examples in docs/backend/examples (`.rule.json`) are the test fixtures. commonTest cannot read files on
 * every target (no java.io on iOS), so they are embedded as Kotlin string constants at build time.
 */
val generateRuleFixtures = tasks.register("generateRuleFixtures") {
    val examples = rootProject.layout.projectDirectory.dir("../docs/backend/examples")
    val input = files(examples.asFileTree.matching { include("*.rule.json") })
    val output = layout.buildDirectory.dir("generated/ruleFixtures/kotlin")
    inputs.files(input).withPathSensitivity(PathSensitivity.NAME_ONLY)
    outputs.dir(output)
    doLast {
        val dir = output.get().asFile.resolve("com/sarkarimatch/core/eligibility").apply { mkdirs() }
        val entries = input.files.sortedBy { it.name }.joinToString("\n") { file ->
            val name = file.name.removeSuffix(".rule.json")
            "        \"$name\" to \$\$\$\"\"\"${file.readText()}\"\"\","
        }
        dir.resolve("RuleFixtureSources.kt").writeText(
            """
            |// Generated from docs/backend/examples by :core:eligibility:generateRuleFixtures. Do not edit.
            |package com.sarkarimatch.core.eligibility
            |
            |internal object RuleFixtureSources {
            |    val byName: Map<String, String> = mapOf(
            |$entries
            |    )
            |}
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.kotlinx.serialization.json)
        }
        commonTest {
            kotlin.srcDir(generateRuleFixtures)
        }
    }
}
