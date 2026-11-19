plugins {
    alias(libs.plugins.sm.android.library)
    alias(libs.plugins.sm.android.compose)
}

android {
    namespace = "com.sarkarimatch.core.designsystem"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.kotlinx.collections.immutable)
}

dependencies {
    testImplementation(libs.kotlinx.serialization.json)
}

// TokensParityTest reads the design tokens straight from the docs.
tasks.withType<Test>().configureEach {
    systemProperty("sm.tokensJson", rootProject.file("../docs/design/tokens.json").absolutePath)
}
