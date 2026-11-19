package com.sarkarimatch.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.sarkarimatch.core.model.AppLanguage

/** The app language as resolved by AppCompat per-app locales (falls back to Hindi). */
@Composable
@ReadOnlyComposable
fun currentAppLanguage(): AppLanguage {
    val locales = LocalConfiguration.current.locales
    return AppLanguage.fromTag(if (locales.isEmpty) null else locales[0].language) ?: AppLanguage.DEFAULT
}
