plugins {
    alias(libs.plugins.sm.android.library)
    alias(libs.plugins.sm.hilt)
}

android {
    namespace = "com.sarkarimatch.core.notifications"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(libs.androidx.core.ktx)
}
