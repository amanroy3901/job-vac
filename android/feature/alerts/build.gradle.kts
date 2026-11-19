plugins {
    alias(libs.plugins.sm.android.feature)
}

android {
    namespace = "com.sarkarimatch.feature.alerts"
}

dependencies {
    implementation(projects.core.notifications)
}
