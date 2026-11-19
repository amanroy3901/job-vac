plugins {
    alias(libs.plugins.sm.android.library)
    alias(libs.plugins.sm.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sarkarimatch.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.eligibility)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(projects.core.testing)
}
