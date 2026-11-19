package com.sarkarimatch.core.data.prefs

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sarkarimatch.core.model.AppLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app language (i18n A1): AppCompat stores the choice (`autoStoreLocales` on API < 33, the system on 33+)
 * and recreates activities; the code is mirrored into DataStore for code without an Activity.
 */
@Singleton
class LanguageController @Inject constructor(private val prefs: UserPreferencesRepository) {
    fun current(): AppLanguage? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else AppLanguage.fromTag(locales[0]?.language)
    }

    suspend fun set(language: AppLanguage) {
        prefs.setAppLanguage(language)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }
}
