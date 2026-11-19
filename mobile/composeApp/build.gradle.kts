// Shared UI for Android and iOS (Compose Multiplatform): every screen, navigation, ViewModels and Koin wiring.
// Android: consumed by :androidApp (AGP 9 keeps com.android.application out of KMP modules).
// iOS: built into the static framework "ComposeApp" that iosApp/ (SwiftUI) embeds via MainViewController.
plugins {
    alias(libs.plugins.sm.kmp.android.library)
    alias(libs.plugins.sm.kmp.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Swift implements the Ios*Bridge protocols and calls IosPushEvents from :core:data.
            export(projects.core.data)
            export(projects.core.model)
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.core.data)
            api(projects.core.model)
            implementation(projects.core.designsystem)
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            implementation(libs.jb.navigation.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "com.sarkarimatch.app.resources"
    generateResClass = always
}
