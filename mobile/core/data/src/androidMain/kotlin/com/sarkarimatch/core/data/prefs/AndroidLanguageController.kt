package com.sarkarimatch.core.data.prefs

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sarkarimatch.core.model.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android per-app language (i18n A1): AppCompat stores the choice (`autoStoreLocales` on API < 33, the system on
 * 33+) and recreates activities, so Compose resources resolve in the new locale; DataStore keeps a mirror for code
 * without an Activity and for the Compose theme.
 */
class AndroidLanguageController(private val prefs: UserPreferencesRepository) : PrefsLanguageController(prefs) {
    /** The locale AppCompat / the system holds (may have been changed in system settings on Android 13+). */
    fun appCompatLanguage(): AppLanguage? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else AppLanguage.fromTag(locales[0]?.language)
    }

    override suspend fun set(language: AppLanguage) {
        prefs.setAppLanguage(language)
        withContext(Dispatchers.Main) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}
