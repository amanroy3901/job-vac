package com.sarkarimatch.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.sarkarimatch.core.model.ExamCategoryTile

/** A container + icon/content pair (category tiles, chips). */
@Immutable
data class TonePair(val container: Color, val content: Color)

/** tokens.json `color.*.ext.*`: roles Material 3 has no slot for. */
@Immutable
data class SarkariColors(
    val success: TonePair,
    val warning: TonePair,
    val urgent: TonePair,
    val today: TonePair,
    val marigold: TonePair,
    val marigoldContainer: TonePair,
    val marigoldIcon: Color,
    val card: Color,
    val sunken: Color,
    val hairline: Color,
    val muted: Color,
    val glass: Color,
    val shadowTint: Color,
    val police: TonePair,
    val ssc: TonePair,
    val railway: TonePair,
    val bank: TonePair,
    val state: TonePair,
) {
    fun tile(category: ExamCategoryTile): TonePair = when (category) {
        ExamCategoryTile.Police -> police
        ExamCategoryTile.Ssc -> ssc
        ExamCategoryTile.Railway -> railway
        ExamCategoryTile.Bank -> bank
        ExamCategoryTile.State -> state
    }
}

internal val LightSarkari = SarkariColors(
    success = TonePair(Color(0xFFE2F0E8), Color(0xFF1E5B46)),
    warning = TonePair(Color(0xFFF8EDD4), Color(0xFF8A5A00)),
    urgent = TonePair(Color(0xFFFBE4DC), Color(0xFF8A2A15)),
    today = TonePair(Color(0xFFB3361E), Color(0xFFFFFFFF)),
    marigold = TonePair(Color(0xFFF2A93B), Color(0xFF3A2405)),
    marigoldContainer = TonePair(Color(0xFFFDEFD6), Color(0xFF8A5200)),
    marigoldIcon = Color(0xFFB06E00),
    card = Color(0xFFFFFFFF),
    sunken = Color(0xFFF5ECDF),
    hairline = Color(0xFFEFE4D4),
    muted = Color(0xFF6E5D4E),
    glass = Color(0xE6FBF5EC),
    shadowTint = Color(0xFF78460A),
    police = TonePair(Color(0xFFFDEBD3), Color(0xFF9A5B00)),
    ssc = TonePair(Color(0xFFE2F0E8), Color(0xFF1E5B46)),
    railway = TonePair(Color(0xFFFBE4DC), Color(0xFFA23A1E)),
    bank = TonePair(Color(0xFFE1EEF2), Color(0xFF1D5E73)),
    state = TonePair(Color(0xFFF1E4F2), Color(0xFF7A3D80)),
)

internal val DarkSarkari = SarkariColors(
    success = TonePair(Color(0xFF1D3329), Color(0xFF86D4B2)),
    warning = TonePair(Color(0xFF362A12), Color(0xFFEBC46E)),
    urgent = TonePair(Color(0xFF3E2018), Color(0xFFFFC9B8)),
    today = TonePair(Color(0xFFFF8A6B), Color(0xFF2A1205)),
    marigold = TonePair(Color(0xFFF4B755), Color(0xFF2A1A02)),
    marigoldContainer = TonePair(Color(0xFF3A2A12), Color(0xFFF4B755)),
    marigoldIcon = Color(0xFFF4B755),
    card = Color(0xFF231C15),
    sunken = Color(0xFF2D241B),
    hairline = Color(0xFF3A2F24),
    muted = Color(0xFFC2B19E),
    glass = Color(0xE617120D),
    shadowTint = Color(0xFF000000),
    police = TonePair(Color(0xFF3A2A14), Color(0xFFF4B755)),
    ssc = TonePair(Color(0xFF1D3329), Color(0xFF86D4B2)),
    railway = TonePair(Color(0xFF3E2018), Color(0xFFFF9F85)),
    bank = TonePair(Color(0xFF16303A), Color(0xFF8CCFE4)),
    state = TonePair(Color(0xFF33203A), Color(0xFFE3A8EA)),
)

internal val LocalSarkariColors = staticCompositionLocalOf { LightSarkari }
