package com.sarkarimatch.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sarkarimatch.core.designsystem.R
import com.sarkarimatch.core.model.Script

/*
 * tokens.json font.*: Baloo 2 600 (display: headings, prices, calendar numbers) + Hind 400/600 (body, labels).
 * Both are Downloadable Google Fonts (0 KB in the APK). Until they arrive, or on devices without Google Play
 * services, the next font in the family (system sans: Noto Sans Devanagari / Roboto) is used.
 */

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val baloo2 = GoogleFont("Baloo 2")
private val hind = GoogleFont("Hind")

val DisplayFamily = FontFamily(
    Font(googleFont = baloo2, fontProvider = provider, weight = FontWeight.SemiBold),
    // Fallback while downloading / without GMS: the system sans covers Devanagari + Latin.
    Font(android.graphics.Typeface.SANS_SERIF.let { R.font.sm_fallback_sans }, FontWeight.SemiBold),
)

val PrimaryFamily = FontFamily(
    Font(googleFont = hind, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = hind, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(R.font.sm_fallback_sans, FontWeight.Normal),
    Font(R.font.sm_fallback_sans, FontWeight.SemiBold),
)

private data class Role(val display: Boolean, val size: Int, val latin: Int, val devanagari: Int, val weight: FontWeight)

/** The 4 reading sizes 28 / 19 / 16 / 14 (+ 12 for tags), weights 400 / 600. */
private val Roles = mapOf(
    "displaySmall" to Role(true, 28, 36, 40, FontWeight.SemiBold),
    "headlineMedium" to Role(true, 28, 36, 40, FontWeight.SemiBold),
    "headlineSmall" to Role(true, 28, 36, 40, FontWeight.SemiBold),
    "titleLarge" to Role(true, 19, 26, 30, FontWeight.SemiBold),
    "titleMedium" to Role(true, 19, 26, 30, FontWeight.SemiBold),
    "titleSmall" to Role(false, 16, 24, 24, FontWeight.SemiBold),
    "bodyLarge" to Role(false, 16, 24, 24, FontWeight.Normal),
    "bodyMedium" to Role(false, 16, 24, 24, FontWeight.Normal),
    "bodySmall" to Role(false, 14, 20, 22, FontWeight.Normal),
    "labelLarge" to Role(false, 16, 24, 24, FontWeight.SemiBold),
    "labelMedium" to Role(false, 14, 20, 22, FontWeight.SemiBold),
    "labelSmall" to Role(false, 12, 16, 18, FontWeight.SemiBold),
)

/**
 * Typography for the active script: Latin uses `lineHeight`, every other script starts from
 * `lineHeightDevanagari` and multiplies by the script's multiplier (i18n-and-design-system.md A3).
 */
fun smTypography(script: Script): Typography {
    fun style(name: String): TextStyle {
        val r = Roles.getValue(name)
        val lh: TextUnit = if (script == Script.Latin) {
            r.latin.sp
        } else {
            (r.devanagari * script.lineHeightMultiplier).sp
        }
        return TextStyle(
            fontFamily = if (r.display) DisplayFamily else PrimaryFamily,
            fontWeight = r.weight,
            fontSize = r.size.sp,
            lineHeight = lh,
            letterSpacing = 0.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
    val base = Typography()
    return base.copy(
        displayLarge = style("displaySmall"),
        displayMedium = style("displaySmall"),
        displaySmall = style("displaySmall"),
        headlineLarge = style("headlineMedium"),
        headlineMedium = style("headlineMedium"),
        headlineSmall = style("headlineSmall"),
        titleLarge = style("titleLarge"),
        titleMedium = style("titleMedium"),
        titleSmall = style("titleSmall"),
        bodyLarge = style("bodyLarge"),
        bodyMedium = style("bodyMedium"),
        bodySmall = style("bodySmall"),
        labelLarge = style("labelLarge"),
        labelMedium = style("labelMedium"),
        labelSmall = style("labelSmall"),
    )
}

/** Tabular figures for countdowns, fees and dates (tokens font.family.numeric). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
