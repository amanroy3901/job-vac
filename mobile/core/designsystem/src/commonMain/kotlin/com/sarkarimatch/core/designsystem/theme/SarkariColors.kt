package com.sarkarimatch.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.sarkarimatch.core.model.ExamCategoryTile

/** A container + content pair, used by generic components (chips, ribbons). */
@Immutable
data class TonePair(val container: Color, val content: Color)

/** tokens.json `color.*.ext.category.*`. */
@Immutable
data class CategoryColors(val container: Color, val icon: Color)

/**
 * tokens.json v2.0 `color.*.ext.*` (docs/design/02-design-system.md §2.7): roles Material 3 has no slot for.
 * Names mirror the tokens one-to-one. Marigold is a *fill* colour only; marigold text/icons use [marigoldIcon]
 * (or `onTertiaryContainer`). Muted text is `MaterialTheme.colorScheme.onSurfaceVariant` (= ext.cocoaMuted).
 */
@Immutable
data class SarkariColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val urgent: Color,
    val onUrgent: Color,
    val urgentContainer: Color,
    val onUrgentContainer: Color,
    val today: Color,
    val onToday: Color,
    val marigold: Color,
    val onMarigold: Color,
    val marigoldContainer: Color,
    val onMarigoldContainer: Color,
    val marigoldIcon: Color,
    val snackbarAction: Color,
    val card: Color,
    val sunken: Color,
    val hairline: Color,
    val glass: Color,
    val shadowTint: Color,
    val police: CategoryColors,
    val ssc: CategoryColors,
    val railway: CategoryColors,
    val bank: CategoryColors,
    val state: CategoryColors,
) {
    // Aliases (tokens ext.terracotta*): same values as today / urgent.
    val terracotta: Color get() = today
    val onTerracotta: Color get() = onToday
    val terracottaContainer: Color get() = urgentContainer
    val onTerracottaContainer: Color get() = onUrgentContainer

    fun category(tile: ExamCategoryTile): CategoryColors = when (tile) {
        ExamCategoryTile.Police -> police
        ExamCategoryTile.Ssc -> ssc
        ExamCategoryTile.Railway -> railway
        ExamCategoryTile.Bank -> bank
        ExamCategoryTile.State -> state
    }
}

internal val LightSarkari = SarkariColors(
    success = Color(0xFF1E5B46),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFE2F0E8),
    onSuccessContainer = Color(0xFF1E5B46),
    warning = Color(0xFF8A5A00),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFF8EDD4),
    onWarningContainer = Color(0xFF8A5A00),
    urgent = Color(0xFFB3361E),
    onUrgent = Color(0xFFFFFFFF),
    urgentContainer = Color(0xFFFBE4DC),
    onUrgentContainer = Color(0xFF8A2A15),
    today = Color(0xFFB3361E),
    onToday = Color(0xFFFFFFFF),
    marigold = Color(0xFFF2A93B),
    onMarigold = Color(0xFF3A2405),
    marigoldContainer = Color(0xFFFDEFD6),
    onMarigoldContainer = Color(0xFF8A5200),
    marigoldIcon = Color(0xFF9A5B00),
    snackbarAction = Color(0xFFF2A93B),
    card = Color(0xFFFFFFFF),
    sunken = Color(0xFFF5ECDF),
    hairline = Color(0xFFEFE4D4),
    glass = Color(0xF7FBF5EC),
    shadowTint = Color(0xFF78460A),
    police = CategoryColors(Color(0xFFFDEBD3), Color(0xFF9A5B00)),
    ssc = CategoryColors(Color(0xFFE2F0E8), Color(0xFF1E5B46)),
    railway = CategoryColors(Color(0xFFFBE4DC), Color(0xFFA23A1E)),
    bank = CategoryColors(Color(0xFFE1EEF2), Color(0xFF1D5E73)),
    state = CategoryColors(Color(0xFFF1E4F2), Color(0xFF7A3D80)),
)

internal val DarkSarkari = SarkariColors(
    success = Color(0xFF86D4B2),
    onSuccess = Color(0xFF0D2A1F),
    successContainer = Color(0xFF1D3329),
    onSuccessContainer = Color(0xFF86D4B2),
    warning = Color(0xFFEBC46E),
    onWarning = Color(0xFF2A1A02),
    warningContainer = Color(0xFF362A12),
    onWarningContainer = Color(0xFFEBC46E),
    urgent = Color(0xFFFF8A6B),
    onUrgent = Color(0xFF2A1205),
    urgentContainer = Color(0xFF3E2018),
    onUrgentContainer = Color(0xFFFFC9B8),
    today = Color(0xFFFF8A6B),
    onToday = Color(0xFF2A1205),
    marigold = Color(0xFFF4B755),
    onMarigold = Color(0xFF2A1A02),
    marigoldContainer = Color(0xFF3A2A12),
    onMarigoldContainer = Color(0xFFF4B755),
    marigoldIcon = Color(0xFFF4B755),
    snackbarAction = Color(0xFF8A5200),
    card = Color(0xFF231C15),
    sunken = Color(0xFF2D241B),
    hairline = Color(0xFF3A2F24),
    glass = Color(0xF717120D),
    shadowTint = Color(0xFF000000),
    police = CategoryColors(Color(0xFF3A2A14), Color(0xFFF4B755)),
    ssc = CategoryColors(Color(0xFF1D3329), Color(0xFF86D4B2)),
    railway = CategoryColors(Color(0xFF3E2018), Color(0xFFFF9F85)),
    bank = CategoryColors(Color(0xFF16303A), Color(0xFF8CCFE4)),
    state = CategoryColors(Color(0xFF33203A), Color(0xFFE3A8EA)),
)

internal val LocalSarkariColors = staticCompositionLocalOf { LightSarkari }
