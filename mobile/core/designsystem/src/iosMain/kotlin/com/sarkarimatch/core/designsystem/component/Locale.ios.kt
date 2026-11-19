package com.sarkarimatch.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.sarkarimatch.core.model.AppLanguage
import platform.Foundation.NSUserDefaults

private const val APPLE_LANGUAGES = "AppleLanguages"

/**
 * iOS: writes the choice to `AppleLanguages` (read by NSLocale.preferredLanguages, which Compose resources use, and
 * by UIKit on the next launch, so system UI such as permission dialogs follows too) and re-keys the content so
 * every string is resolved again.
 */
@Composable
actual fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val defaults = NSUserDefaults.standardUserDefaults
    val current = (defaults.arrayForKey(APPLE_LANGUAGES)?.firstOrNull() as? String)?.substringBefore('-')
    if (current != language.tag) {
        defaults.setObject(listOf(language.tag), forKey = APPLE_LANGUAGES)
    }
    key(language) { content() }
}
