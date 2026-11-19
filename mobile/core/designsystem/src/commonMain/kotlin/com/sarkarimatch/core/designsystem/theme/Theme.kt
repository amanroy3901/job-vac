package com.sarkarimatch.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.sarkarimatch.core.designsystem.component.LocalAppLanguage
import com.sarkarimatch.core.model.AppLanguage

/**
 * The Marigold theme (Direction C), identical on Android and iOS. Light-first; dark fully specified. Dynamic colour
 * is deliberately off.
 *
 * @param language the app language; selects line heights (Devanagari needs more room than Latin) and is exposed to
 * components as [LocalAppLanguage] (LocalizedText.pick, dates).
 */
@Composable
fun SmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    language: AppLanguage = AppLanguage.DEFAULT,
    content: @Composable () -> Unit,
) {
    val typography = smTypography(language.script)
    CompositionLocalProvider(
        LocalSarkariColors provides if (darkTheme) DarkSarkari else LightSarkari,
        LocalSpacing provides Spacing(),
        LocalAppLanguage provides language,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = typography,
            shapes = SmShapes,
            content = content,
        )
    }
}

object SmTheme {
    val colors: SarkariColors
        @Composable @ReadOnlyComposable
        get() = LocalSarkariColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current
}
