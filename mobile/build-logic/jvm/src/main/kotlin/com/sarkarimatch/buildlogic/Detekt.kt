package com.sarkarimatch.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Detekt with the shared config in mobile/config/detekt/detekt.yml. Run with `./gradlew detekt`.
 * KMP modules keep their code in `src/<sourceSet>/kotlin` (commonMain, androidMain, iosMain, *Test), so the plain
 * `detekt` task scans all of `src/` (no type resolution; the per-target `detekt<Target>` tasks are not used).
 */
fun Project.configureDetekt() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom(files("src"))
        parallel = true
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            sarif.required.set(false)
            txt.required.set(false)
            xml.required.set(false)
            md.required.set(false)
        }
    }
    // detekt 1.23.x is compiled against Kotlin 2.0.21; keep its own classpath on that version even though the
    // project uses a newer Kotlin (otherwise KGP's version alignment breaks the detekt CLI).
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
        }
    }
}
