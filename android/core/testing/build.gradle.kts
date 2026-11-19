plugins {
    alias(libs.plugins.sm.android.library)
}

android {
    namespace = "com.sarkarimatch.core.testing"
}

dependencies {
    api(projects.core.model)
    api(projects.core.data)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
