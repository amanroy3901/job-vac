// On-device eligibility evaluator (docs/backend/matching-engine.md). Pure Kotlin/JVM, no I/O, no Android:
// the same rules run on the phone; later this is replaced by / merged into the shared KMP :matching-core.
plugins {
    alias(libs.plugins.sm.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.serialization.json)
}
