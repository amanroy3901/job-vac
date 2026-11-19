plugins {
    alias(libs.plugins.sm.android.feature)
}

android {
    namespace = "com.sarkarimatch.feature.pro"
}

dependencies {
    implementation(projects.core.billing)
}
