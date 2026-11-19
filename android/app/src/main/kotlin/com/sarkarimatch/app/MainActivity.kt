package com.sarkarimatch.app

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarkarimatch.core.designsystem.component.currentAppLanguage
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.model.AppLanguage
import com.sarkarimatch.core.notifications.NotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Single activity. AppCompatActivity for per-app language on API < 33. Edge-to-edge, splash, Compose. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var notificationChannels: NotificationChannels

    private val startViewModel: StartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyDefaultLanguageOnFirstRun()
        splash.setKeepOnScreenCondition { startViewModel.state.value == StartState.Deciding }
        // Channel names follow the app language; this Activity context carries it on every API level.
        notificationChannels.ensureCreated(this)

        setContent {
            val start by startViewModel.state.collectAsStateWithLifecycle()
            SmTheme(script = currentAppLanguage().script) {
                if (start != StartState.Deciding) {
                    SmApp(
                        startOnboarding = start == StartState.Onboarding,
                        onOnboardingFinished = startViewModel::onOnboardingFinished,
                    )
                }
            }
        }
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

    private companion object {
        const val KEY_LOCALE_INITIALISED = "locale_initialised"
    }
}
