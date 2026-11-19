package com.sarkarimatch.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sarkarimatch.core.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** File name of the DataStore `app_prefs` (data-layer.md §4); the directory is platform-specific. */
const val APP_PREFS_FILE = "app_prefs.preferences_pb"

/** Creates the DataStore at [path] (Android: filesDir/datastore/…, iOS: Documents/…). Call once per process. */
fun createAppPrefsDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

/** DataStore `app_prefs`. Not encrypted: nothing secret lives here. Same file format on Android and iOS. */
class UserPreferencesRepository(private val store: DataStore<Preferences>) {
    private object Keys {
        val appLanguage = stringPreferencesKey("app_language")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val installationId = stringPreferencesKey("installation_id")
        val devUid = stringPreferencesKey("dev_uid")
    }

    /** The chosen app language (Hindi until the user picks another, i18n A1). */
    val appLanguage: Flow<AppLanguage> = store.data.map {
        AppLanguage.fromTag(it[Keys.appLanguage]) ?: AppLanguage.DEFAULT
    }

    /** Whether the user has chosen a language at all (vs. the Hindi default). */
    val appLanguageChosen: Flow<Boolean> = store.data.map { it[Keys.appLanguage] != null }

    val onboardingDone: Flow<Boolean> = store.data.map { it[Keys.onboardingDone] ?: false }

    suspend fun setAppLanguage(language: AppLanguage) {
        store.edit { it[Keys.appLanguage] = language.tag }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        store.edit { it[Keys.onboardingDone] = done }
    }

    /**
     * Stable per-install id sent as `installation_id` on `/v1/session` (stand-in for the Firebase Installations ID
     * until FE-06 wires Firebase).
     */
    suspend fun installationId(): String = getOrCreate(Keys.installationId, "i_")

    /** Dev-auth only: the uid behind `Bearer dev:<uid>` (one anonymous dev account per install). */
    suspend fun devUid(): String = getOrCreate(Keys.devUid, "dev_")

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun getOrCreate(key: Preferences.Key<String>, prefix: String): String {
        store.data.first()[key]?.let { return it }
        var value = ""
        store.edit { prefs -> value = prefs[key] ?: (prefix + Uuid.random().toHexString()).also { prefs[key] = it } }
        return value
    }
}
