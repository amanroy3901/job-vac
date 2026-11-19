package com.sarkarimatch.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.sarkarimatch.core.model.AppLanguage

/** The app language chosen on S02/S40 (Hindi until the user picks another). Provided by [SmTheme][com.sarkarimatch.core.designsystem.theme.SmTheme]. */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.DEFAULT }

/** The app language for LocalizedText.pick and date formatting. */
@Composable
@ReadOnlyComposable
fun currentAppLanguage(): AppLanguage = LocalAppLanguage.current

/**
 * Makes Compose resources (strings, plurals) resolve in [language] regardless of the device language.
 *
 * - Android: the per-app locale (AppCompat `setApplicationLocales`) already recreates the Activity with the right
 *   configuration, which Compose resources read; nothing to do here.
 * - iOS: there is no per-app locale API for a running app, so the choice is written to `AppleLanguages` and provided
 *   to Compose resources directly (Compose Multiplatform "custom locale" recipe); [content] is re-keyed on change.
 */
@Composable
expect fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit)
