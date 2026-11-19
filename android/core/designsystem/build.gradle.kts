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
