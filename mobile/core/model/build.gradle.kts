// Domain models shared by Android and iOS: languages, LocalizedText, profile enums, verdicts, IST dates, formatting.
plugins {
    alias(libs.plugins.sm.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
    }
}
