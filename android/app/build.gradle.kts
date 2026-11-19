plugins {
    alias(libs.plugins.sm.android.application)
    alias(libs.plugins.sm.android.compose)
    alias(libs.plugins.sm.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val enabledLocales = providers.gradleProperty("sm.enabledLocales").get().split(',').map(String::trim)

android {
    namespace = "com.sarkarimatch.app"

    defaultConfig {
        applicationId = "in.sarkarimatch.app" // placeholder (domain not verified, PRD founder decision 3)
        versionCode = 1
        versionName = "0.1.0"
    }

    // Only enabled locales ship; must match res/xml/locales_config.xml (i18n-and-design-system.md A1).
    androidResources {
        localeFilters += enabledLocales
        generateLocaleConfig = false
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "env"
    productFlavors {
        create("prod") {
            dimension = "env"
            buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("sm.apiBaseUrl.prod").get()}\"")
        }
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("sm.apiBaseUrl.staging").get()}\"")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // TODO(FE-30): upload-key signing config from CI secrets. Debug-signed until then so it installs.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.data)
    implementation(projects.core.notifications)
    implementation(projects.core.billing)
    implementation(projects.core.ads)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.alerts)
    implementation(projects.feature.jobs)
    implementation(projects.feature.applications)
    implementation(projects.feature.pro)
    implementation(projects.feature.profile)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}
