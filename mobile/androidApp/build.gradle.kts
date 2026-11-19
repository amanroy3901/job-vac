// Android application shell: MainActivity, Application (Koin start), manifest, launcher icon, splash, flavours.
// All UI and logic are in the KMP modules (:composeApp, :core:*).
plugins {
    alias(libs.plugins.sm.android.application)
}

val enabledLocales = providers.gradleProperty("sm.enabledLocales").get().split(',').map(String::trim)

android {
    namespace = "com.sarkarimatch.app"

    defaultConfig {
        applicationId = "in.sarkarimatch.app" // placeholder (domain not verified, PRD founder decision 3); same as the iOS bundle id
        versionCode = 1
        versionName = "0.1.0"
        // Backend OAuth web client id for Sign in with Google (empty → guest only until configured).
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${providers.gradleProperty("sm.googleServerClientId").getOrElse("")}\"")
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
            buildConfigField("boolean", "DEV_AUTH", "false")
        }
        // Local development against `backend/` (`make run` on the host): the emulator reaches the host's
        // localhost:8080 at 10.0.2.2. Override for a phone on the LAN: -Psm.apiBaseUrl.dev=http://192.168.1.10:8080/
        // Cleartext HTTP and the backend's dev token auth are enabled ONLY in this flavour (src/dev/).
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("sm.apiBaseUrl.dev").get()}\"")
            buildConfigField("boolean", "DEV_AUTH", "true")
        }
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("sm.apiBaseUrl.staging").get()}\"")
            buildConfigField("boolean", "DEV_AUTH", "false")
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
    implementation(projects.composeApp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.jb.lifecycle.viewmodel.compose)
}
