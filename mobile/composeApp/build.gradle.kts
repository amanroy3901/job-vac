// Shared UI for Android and iOS (Compose Multiplatform): every screen, navigation, ViewModels and Koin wiring.
// Android: consumed by :androidApp (AGP 9 keeps com.android.application out of KMP modules).
// iOS: built into the static framework "ComposeApp" that iosApp/ (SwiftUI) embeds via MainViewController.
plugins {
    alias(libs.plugins.sm.kmp.android.library)
    alias(libs.plugins.sm.kmp.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Dev-only desktop (JVM) target: runs the shared UI + data layer on Linux/macOS against a local backend
    // (`./gradlew :composeApp:run`), and hosts the end-to-end test + headless screenshots (desktopTest). Not shipped.
    jvm("desktop")
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
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "com.sarkarimatch.app.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.sarkarimatch.app.DesktopMainKt"
    }
}

// The end-to-end test talks to a running local backend; it is skipped unless -Psm.e2e=true (see mobile/README.md).
tasks.named<Test>("desktopTest") {
    val e2e = providers.gradleProperty("sm.e2e").orElse("false")
    val baseUrl = providers.gradleProperty("sm.e2e.baseUrl").orElse("http://localhost:8080/")
    systemProperty("sm.e2e", e2e.get())
    systemProperty("sm.e2e.baseUrl", baseUrl.get())
    systemProperty("sm.screenshots.dir", rootProject.layout.projectDirectory.dir("docs/screenshots/e2e").asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
    outputs.upToDateWhen { false }
    testLogging { showStandardStreams = true; events("passed", "skipped", "failed") }
}
