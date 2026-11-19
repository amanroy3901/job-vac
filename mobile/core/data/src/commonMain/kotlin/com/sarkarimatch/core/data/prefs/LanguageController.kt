package com.sarkarimatch.core.data.prefs

import com.sarkarimatch.core.model.AppLanguage
import kotlinx.coroutines.flow.Flow

/**
 * Per-app language (i18n A1). The choice is always mirrored into DataStore (workers, notification text, iOS).
 * - Android: AppCompat per-app locales store it too and recreate the Activity (API < 33 via `autoStoreLocales`).
 * - iOS: the Compose root re-keys on [language] and writes `AppleLanguages` (designsystem `ProvideAppLocale`).
 */
interface LanguageController {
    /** The app language; Hindi until the user chooses (founder decision 1). */
    val language: Flow<AppLanguage>

    suspend fun set(language: AppLanguage)
}

/** Shared part: DataStore only. iOS uses it as is; Android wraps it with AppCompat. */
open class PrefsLanguageController(private val prefs: UserPreferencesRepository) : LanguageController {
    override val language: Flow<AppLanguage> = prefs.appLanguage

    override suspend fun set(language: AppLanguage) = prefs.setAppLanguage(language)
}
