plugins {
    alias(libs.plugins.sm.android.library)
    alias(libs.plugins.sm.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sarkarimatch.core.network"
}

dependencies {
    api(projects.core.model)
    api(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
}
