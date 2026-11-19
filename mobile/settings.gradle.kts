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
        google()
        mavenCentral()
    }
}

rootProject.name = "sarkari-match"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Pure Kotlin/JVM modules: always included, buildable and testable without the Android SDK.
include(":core:model")
include(":core:eligibility")

/*
 * Android modules need the Android SDK and Google Maven (AGP, AndroidX). They are skipped when
 * -Psm.jvmOnly=true is passed, or when no SDK can be found (no ANDROID_HOME / ANDROID_SDK_ROOT and no
 * sdk.dir in local.properties). Example: ./gradlew -Psm.jvmOnly=true :core:eligibility:test
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
    include(":app")
    include(":core:designsystem")
    include(":core:network")
    include(":core:database")
    include(":core:data")
    include(":core:notifications")
    include(":core:billing")
    include(":core:ads")
    include(":core:testing")
    include(":feature:onboarding")
    include(":feature:alerts")
    include(":feature:jobs")
    include(":feature:applications")
    include(":feature:pro")
    include(":feature:profile")
} else {
    logger.warn(
        "Sarkari Match: Android SDK not found or -Psm.jvmOnly=true; only the JVM modules " +
            "(:core:model, :core:eligibility) are included in this build.",
    )
}
