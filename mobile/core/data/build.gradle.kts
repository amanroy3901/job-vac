// Data layer shared by Android and iOS (docs/frontend/data-layer.md): Ktor API client, Room KMP database,
// DataStore preferences, repositories, the on-device MatchEngine adapter, and the platform-service interfaces
// (billing, sign-in, push, local reminders, ads, app language) with their Android implementations. iOS
// implementations of billing / sign-in / push / ads are Swift classes in iosApp/ that implement the Ios*Bridge
// protocols exported from this module (iosMain).
plugins {
    alias(libs.plugins.sm.kmp.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        // The notification channel names (androidMain/res) are Android resources.
        androidResources.enable = true
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(projects.core.eligibility)
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.datastore.preferences)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.work.runtime)
            implementation(libs.play.billing.ktx)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play)
            implementation(libs.googleid)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
