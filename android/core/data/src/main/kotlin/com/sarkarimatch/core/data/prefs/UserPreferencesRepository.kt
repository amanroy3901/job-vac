package com.sarkarimatch.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sarkarimatch.core.model.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPrefs: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

/** DataStore `app_prefs` (data-layer.md §4). Not encrypted: nothing secret lives here. */
@Singleton
class UserPreferencesRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val appLanguage = stringPreferencesKey("app_language")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    /** Mirror of the per-app locale, for workers and notification composition (no Activity there). */
    val appLanguage: Flow<AppLanguage> = context.appPrefs.data.map {
        AppLanguage.fromTag(it[Keys.appLanguage]) ?: AppLanguage.DEFAULT
    }

    val onboardingDone: Flow<Boolean> = context.appPrefs.data.map { it[Keys.onboardingDone] ?: false }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.appPrefs.edit { it[Keys.appLanguage] = language.tag }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.appPrefs.edit { it[Keys.onboardingDone] = done }
    }
}
