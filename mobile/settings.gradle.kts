pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "sarkari-match"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Pure Kotlin Multiplatform modules (jvm + iOS targets): always included, testable without the Android SDK.
include(":core:model")
include(":core:eligibility")

/*
 * Modules with an Android target need the Android SDK and Google Maven (AGP, AndroidX). They are skipped when
 * -Psm.jvmOnly=true is passed, or when no SDK can be found (no ANDROID_HOME / ANDROID_SDK_ROOT and no
 * sdk.dir in local.properties). Example: ./gradlew -Psm.jvmOnly=true :core:eligibility:jvmTest
 */
val jvmOnlyProperty = providers.gradleProperty("sm.jvmOnly").orNull?.toBoolean()
val sdkFound: Boolean = run {
    val env = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    val localProps = file("local.properties")
    val sdkDir = if (localProps.isFile) {
        java.util.Properties().apply { localProps.inputStream().use { load(it) } }.getProperty("sdk.dir")
    } else {
        null
    }
    listOfNotNull(env, sdkDir).any { file(it).isDirectory }
}
val includeAndroid = when (jvmOnlyProperty) {
    true -> false
    false -> true
    null -> sdkFound
}

if (includeAndroid) {
    include(":core:designsystem") // KMP + CMP: Marigold theme, components, fonts, icons
    include(":core:data") //         KMP: Ktor API, Room KMP, DataStore, repositories, platform service interfaces
    include(":composeApp") //        KMP + CMP: all screens + navigation; produces the iOS framework "ComposeApp"
    include(":androidApp") //        Android application shell (MainActivity, Application, manifest)
} else {
    logger.warn(
        "Sarkari Match: Android SDK not found or -Psm.jvmOnly=true; only the pure KMP modules " +
            "(:core:model, :core:eligibility) are included in this build.",
    )
}
