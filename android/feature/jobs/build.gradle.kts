plugins {
    alias(libs.plugins.sm.android.feature)
}

android {
    namespace = "com.sarkarimatch.feature.jobs"
}

dependencies {
    implementation(projects.core.ads)
}
