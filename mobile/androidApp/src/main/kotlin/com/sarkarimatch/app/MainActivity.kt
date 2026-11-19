package com.sarkarimatch.app

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.sarkarimatch.core.data.prefs.AndroidLanguageController
import com.sarkarimatch.core.model.AppLanguage
import com.sarkarimatch.core.notifications.NotificationChannels
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Single activity. AppCompatActivity for per-app language on API < 33. Edge-to-edge, splash, shared Compose UI. */
class MainActivity : AppCompatActivity() {
    private val notificationChannels: NotificationChannels by inject()
    private val languageController: AndroidLanguageController by inject()
    private val startViewModel: StartViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyDefaultLanguageOnFirstRun()
        syncLanguageFromSystem()
        splash.setKeepOnScreenCondition { startViewModel.state.value == StartState.Deciding }
        // Channel names follow the app language; this Activity context carries it on every API level.
        notificationChannels.ensureCreated(this)

        setContent { App(startViewModel) }
    }

    /**
     * Hindi is the default for every new user (founder decision 1, i18n A1). Done once; afterwards the user's
     * choice on S02/S40 is stored by AppCompat / the system. Recreates the activity once on the very first launch.
     */
    private fun applyDefaultLanguageOnFirstRun() {
        val prefs = getSharedPreferences("sm_bootstrap", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LOCALE_INITIALISED, false)) return
        prefs.edit { putBoolean(KEY_LOCALE_INITIALISED, true) }
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(AppLanguage.DEFAULT.tag))
        }
    }

    /** The language may have been changed in system settings (Android 13+ per-app language); mirror it. */
    private fun syncLanguageFromSystem() {
        val system = languageController.appCompatLanguage() ?: return
        lifecycleScope.launch {
            if (languageController.language.first() != system) languageController.set(system)
        }
    }

    private companion object {
        const val KEY_LOCALE_INITIALISED = "locale_initialised"
    }
}
