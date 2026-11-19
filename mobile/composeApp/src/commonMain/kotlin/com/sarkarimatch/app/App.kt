package com.sarkarimatch.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarkarimatch.core.data.prefs.LanguageController
import com.sarkarimatch.core.designsystem.component.ProvideAppLocale
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.model.AppLanguage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Root composable, identical on Android (MainActivity.setContent) and iOS (MainViewController). Koin must be started
 * by the platform shell first ([initKoin]).
 *
 * @param startViewModel exposed so Android's splash screen can wait for [StartState.Deciding] to end.
 */
@Composable
fun App(startViewModel: StartViewModel = koinViewModel()) {
    val languageController = koinInject<LanguageController>()
    val language by languageController.language.collectAsStateWithLifecycle(AppLanguage.DEFAULT)
    val start by startViewModel.state.collectAsStateWithLifecycle()
    ProvideAppLocale(language) {
        SmTheme(language = language) {
            if (start != StartState.Deciding) {
                SmApp(
                    startOnboarding = start == StartState.Onboarding,
                    onOnboardingFinished = startViewModel::onOnboardingFinished,
                )
            }
        }
    }
}
