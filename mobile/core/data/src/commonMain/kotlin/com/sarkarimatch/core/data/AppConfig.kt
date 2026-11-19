package com.sarkarimatch.core.data

/** Which store / OS this build runs on. Decides the purchase-verification path (Play vs App Store). */
enum class Platform { ANDROID, IOS }

/**
 * Build-time values the shared code needs, supplied by the platform shell: Android from BuildConfig (`env` flavour
 * + build type), iOS from Info.plist (`SMApiBaseUrl`, `CFBundleVersion`) via MainViewController.
 *
 * @property versionCode sent as `X-App-Version` (docs/api/README.md; Android versionCode / iOS CFBundleVersion).
 * @property devAuth local development only (Android `dev` flavour, desktop dev target): account calls send the
 *   backend's dev token `Authorization: Bearer dev:<uid>` (AUTH_MODE=dev) instead of a Firebase ID token. Never true
 *   in prod/staging builds; the backend refuses dev auth when APP_ENV=prod.
 */
data class AppConfig(
    val platform: Platform,
    val apiBaseUrl: String,
    val versionName: String,
    val versionCode: Int,
    val httpLogging: Boolean,
    val devAuth: Boolean = false,
)
