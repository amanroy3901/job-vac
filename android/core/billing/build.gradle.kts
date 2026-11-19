// Play Billing (only payment path, billing.md). The billing-ktx dependency is added with ticket FE-24.
plugins {
    alias(libs.plugins.sm.android.library)
    alias(libs.plugins.sm.hilt)
}

android {
    namespace = "com.sarkarimatch.core.billing"
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.kotlinx.coroutines.android)
}
