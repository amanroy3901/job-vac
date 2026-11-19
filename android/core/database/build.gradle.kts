plugins {
    alias(libs.plugins.sm.android.library)
    alias(libs.plugins.sm.android.room)
    alias(libs.plugins.sm.hilt)
}

android {
    namespace = "com.sarkarimatch.core.database"
}

dependencies {
    api(projects.core.model)
}
