package com.sarkarimatch.core.designsystem.component

import androidx.compose.runtime.Composable
import com.sarkarimatch.core.model.AppLanguage

/** Android: AppCompat per-app locales already put [language] into the Activity configuration. */
@Composable
actual fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) = content()
