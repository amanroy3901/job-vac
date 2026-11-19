package com.sarkarimatch.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import com.sarkarimatch.core.model.Script

/**
 * The Marigold theme (Direction C). Light-first; dark fully specified. Dynamic colour is deliberately off.
 *
 * @param script script of the app language; selects line heights (Devanagari needs more room than Latin).
 */
@Composable
fun SmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    script: Script = Script.Devanagari,
    content: @Composable () -> Unit,
) {
    val typography = remember(script) { smTypography(script) }
    CompositionLocalProvider(
        LocalSarkariColors provides if (darkTheme) DarkSarkari else LightSarkari,
        LocalSpacing provides Spacing(),
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
