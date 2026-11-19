package com.sarkarimatch.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sarkarimatch.core.designsystem.resources.Res
import com.sarkarimatch.core.designsystem.resources.baloo2_semibold
import com.sarkarimatch.core.designsystem.resources.hind_regular
import com.sarkarimatch.core.designsystem.resources.hind_semibold
import com.sarkarimatch.core.model.Script
import org.jetbrains.compose.resources.Font

/*
 * tokens.json font.*: Baloo 2 600 (display: headings, prices, calendar numbers) + Hind 400/600 (body, labels).
 * Downloadable Google Fonts are Android-only, so both are bundled as Compose resources (composeResources/font),
 * subset to Latin + Devanagari and stripped of hinting: ~600 KB raw / ~250 KB compressed in the APK and IPA
 * (Baloo 2 SemiBold 345 KB, Hind 400 + 600 130 KB each). Other scripts (Bengali, Tamil, Urdu, …) fall back to the
 * system fonts (Noto on Android, the iOS system font) until their own families are added (i18n A3).
 */

/** Baloo 2 SemiBold (headings). Compose resource fonts load in composition, hence @Composable. */
@Composable
fun displayFamily(): FontFamily = FontFamily(Font(Res.font.baloo2_semibold, FontWeight.SemiBold))

/** Hind 400 / 600 (body, labels, buttons). */
@Composable
fun primaryFamily(): FontFamily = FontFamily(
    Font(Res.font.hind_regular, FontWeight.Normal),
    Font(Res.font.hind_semibold, FontWeight.SemiBold),
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

/** Typography for the active script, with the bundled Baloo 2 / Hind families. */
@Composable
fun smTypography(script: Script): Typography {
    val display = displayFamily()
    val primary = primaryFamily()
    return remember(script, display, primary) { smTypography(script, display, primary) }
}

/**
 * Latin uses `lineHeight`, every other script starts from `lineHeightDevanagari` and multiplies by the script's
 * multiplier (i18n-and-design-system.md A3). Font families are parameters so tests can use the defaults.
 */
fun smTypography(script: Script, display: FontFamily, primary: FontFamily): Typography {
    fun style(name: String): TextStyle {
        val r = Roles.getValue(name)
        val lh: TextUnit = if (script == Script.Latin) {
            r.latin.sp
        } else {
            (r.devanagari * script.lineHeightMultiplier).sp
        }
        return TextStyle(
            fontFamily = if (r.display) display else primary,
            fontWeight = r.weight,
            fontSize = r.size.sp,
            lineHeight = lh,
            letterSpacing = 0.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            // Balanced heading lines (no one-word orphans such as "हैं"); paragraphs for body/labels.
            lineBreak = if (r.display) LineBreak.Heading else LineBreak.Paragraph,
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
