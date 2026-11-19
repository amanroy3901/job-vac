package com.sarkarimatch.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.sarkarimatch.core.model.AppLanguage
import java.util.Locale

/**
 * Desktop (dev-only target): Compose resources read the JVM default locale, so set it and re-key the content so every
 * string is resolved again in [language].
 */
@Composable
actual fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    if (Locale.getDefault().language != language.tag) Locale.setDefault(Locale.forLanguageTag(language.tag))
    key(language) { content() }
}
