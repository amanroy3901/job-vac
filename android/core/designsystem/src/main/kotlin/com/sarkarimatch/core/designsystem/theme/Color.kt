package com.sarkarimatch.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Direction C "Marigold Home" (docs/design/directions/README.md §C, docs/design/tokens.json color.light/dark).
 * 60 cream · 30 cocoa text + white cards · 10 forest green + marigold. Dynamic colour is off.
 * Hex values are only allowed in this package (feature modules read MaterialTheme / SmTheme.colors).
 */

internal val LightColors = lightColorScheme(
    primary = Color(0xFF1E5B46),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2F0E8),
    onPrimaryContainer = Color(0xFF1E5B46),
    secondary = Color(0xFF5E4D3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5ECDF),
    onSecondaryContainer = Color(0xFF2A1E14),
    tertiary = Color(0xFFF2A93B),
    onTertiary = Color(0xFF3A2405),
    tertiaryContainer = Color(0xFFFDEFD6),
    onTertiaryContainer = Color(0xFF8A5200),
    error = Color(0xFFB3361E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE4DC),
    onErrorContainer = Color(0xFF8A2A15),
    background = Color(0xFFFBF5EC),
    onBackground = Color(0xFF2A1E14),
    surface = Color(0xFFFBF5EC),
    onSurface = Color(0xFF2A1E14),
    surfaceVariant = Color(0xFFF5ECDF),
    onSurfaceVariant = Color(0xFF6E5D4E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF9F3),
    surfaceContainer = Color(0xFFF8F0E5),
    surfaceContainerHigh = Color(0xFFF5ECDF),
    surfaceContainerHighest = Color(0xFFEFE4D4),
    outline = Color(0xFF8C7A68),
    outlineVariant = Color(0xFFEFE4D4),
    inverseSurface = Color(0xFF3A2F24),
    inverseOnSurface = Color(0xFFF6EDE1),
    inversePrimary = Color(0xFF86D4B2),
    scrim = Color(0xFF000000),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF86D4B2),
    onPrimary = Color(0xFF0D2A1F),
    primaryContainer = Color(0xFF1D3329),
    onPrimaryContainer = Color(0xFF86D4B2),
    secondary = Color(0xFFD6C5B2),
    onSecondary = Color(0xFF2A1E14),
    secondaryContainer = Color(0xFF2D241B),
    onSecondaryContainer = Color(0xFFF6EDE1),
    tertiary = Color(0xFFF4B755),
    onTertiary = Color(0xFF2A1A02),
    tertiaryContainer = Color(0xFF3A2A12),
    onTertiaryContainer = Color(0xFFF4B755),
    error = Color(0xFFFF8A6B),
    onError = Color(0xFF2A1205),
    errorContainer = Color(0xFF3E2018),
    onErrorContainer = Color(0xFFFFC9B8),
    background = Color(0xFF17120D),
    onBackground = Color(0xFFF6EDE1),
    surface = Color(0xFF17120D),
    onSurface = Color(0xFFF6EDE1),
    surfaceVariant = Color(0xFF2D241B),
    onSurfaceVariant = Color(0xFFC2B19E),
    surfaceContainerLowest = Color(0xFF120E0A),
    surfaceContainerLow = Color(0xFF231C15),
    surfaceContainer = Color(0xFF282018),
    surfaceContainerHigh = Color(0xFF2D241B),
    surfaceContainerHighest = Color(0xFF3A2F24),
    outline = Color(0xFF9A8875),
    outlineVariant = Color(0xFF3A2F24),
    inverseSurface = Color(0xFFF6EDE1),
    inverseOnSurface = Color(0xFF2A1E14),
    inversePrimary = Color(0xFF1E5B46),
    scrim = Color(0xFF000000),
)
