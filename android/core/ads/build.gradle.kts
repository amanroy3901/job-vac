// Free-tier native ad slot (ads.md). The GMA Next-Gen SDK + UMP are added with ticket FE-27.
plugins {
    alias(libs.plugins.sm.android.library)
    alias(libs.plugins.sm.android.compose)
    alias(libs.plugins.sm.hilt)
}

android {
    namespace = "com.sarkarimatch.core.ads"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
}
