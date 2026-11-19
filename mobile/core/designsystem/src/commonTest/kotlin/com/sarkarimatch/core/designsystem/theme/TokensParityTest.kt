package com.sarkarimatch.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every colour role in docs/design/tokens.json must equal the Kotlin theme (i18n-and-design-system.md B1, D11).
 * A token change in design fails CI until Color.kt / SarkariColors.kt are updated, and vice versa.
 * Common test: runs on the JVM (Android host test) and on the iOS simulator. [TOKENS_JSON] is generated from
 * docs/design/tokens.json at build time (see build.gradle.kts).
 */
class TokensParityTest {
    private val tokens: JsonObject = Json.parseToJsonElement(TOKENS_JSON).jsonObject

    private fun flatten(obj: JsonObject, prefix: String = ""): Map<String, String> = buildMap {
        for ((k, v) in obj) {
            if (k.startsWith("$")) continue
            val o = v as? JsonObject ?: continue
            val value = o["\$value"]
            if (value != null) put(prefix + k, value.jsonPrimitive.content) else putAll(flatten(o, "$prefix$k."))
        }
    }

    private fun hex(c: Color): String {
        val argb = c.toArgb().toLong() and 0xFFFFFFFFL
        val alpha = (argb shr 24) and 0xFF
        val rgb = (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
        return if (alpha == 0xFFL) "#$rgb" else "#" + alpha.toString(16).uppercase().padStart(2, '0') + rgb
    }

    private fun schemeRoles(s: ColorScheme): Map<String, Color> = mapOf(
        "primary" to s.primary, "onPrimary" to s.onPrimary,
        "primaryContainer" to s.primaryContainer, "onPrimaryContainer" to s.onPrimaryContainer,
        "secondary" to s.secondary, "onSecondary" to s.onSecondary,
        "secondaryContainer" to s.secondaryContainer, "onSecondaryContainer" to s.onSecondaryContainer,
        "tertiary" to s.tertiary, "onTertiary" to s.onTertiary,
        "tertiaryContainer" to s.tertiaryContainer, "onTertiaryContainer" to s.onTertiaryContainer,
        "error" to s.error, "onError" to s.onError, "errorContainer" to s.errorContainer, "onErrorContainer" to s.onErrorContainer,
        "background" to s.background, "onBackground" to s.onBackground,
        "surface" to s.surface, "onSurface" to s.onSurface,
        "surfaceVariant" to s.surfaceVariant, "onSurfaceVariant" to s.onSurfaceVariant,
        "surfaceContainerLowest" to s.surfaceContainerLowest, "surfaceContainerLow" to s.surfaceContainerLow,
        "surfaceContainer" to s.surfaceContainer, "surfaceContainerHigh" to s.surfaceContainerHigh,
        "surfaceContainerHighest" to s.surfaceContainerHighest,
        "outline" to s.outline, "outlineVariant" to s.outlineVariant,
        "inverseSurface" to s.inverseSurface, "inverseOnSurface" to s.inverseOnSurface,
        "inversePrimary" to s.inversePrimary, "scrim" to s.scrim,
    )

    private fun extRoles(s: SarkariColors): Map<String, Color> = mapOf(
        "success" to s.success, "onSuccess" to s.onSuccess,
        "successContainer" to s.successContainer, "onSuccessContainer" to s.onSuccessContainer,
        "warning" to s.warning, "onWarning" to s.onWarning,
        "warningContainer" to s.warningContainer, "onWarningContainer" to s.onWarningContainer,
        "urgent" to s.urgent, "onUrgent" to s.onUrgent, "urgentContainer" to s.urgentContainer, "onUrgentContainer" to s.onUrgentContainer,
        "today" to s.today, "onToday" to s.onToday,
        "marigold" to s.marigold, "onMarigold" to s.onMarigold,
        "marigoldContainer" to s.marigoldContainer, "onMarigoldContainer" to s.onMarigoldContainer,
        "marigoldIcon" to s.marigoldIcon, "snackbarAction" to s.snackbarAction,
        "terracotta" to s.terracotta, "onTerracotta" to s.onTerracotta,
        "terracottaContainer" to s.terracottaContainer, "onTerracottaContainer" to s.onTerracottaContainer,
        "card" to s.card, "sunken" to s.sunken, "hairline" to s.hairline, "glass" to s.glass, "shadowTint" to s.shadowTint,
    ) + listOf("police" to s.police, "ssc" to s.ssc, "railway" to s.railway, "bank" to s.bank, "state" to s.state)
        .flatMap { (name, c) -> listOf("category.$name.container" to c.container, "category.$name.icon" to c.icon) }

    private fun check(mode: String, scheme: ColorScheme, ext: SarkariColors) {
        val flat = flatten(tokens.getValue("color").jsonObject.getValue(mode).jsonObject)
        val m3 = schemeRoles(scheme)
        val extKotlin = extRoles(ext)
        val mismatches = flat.mapNotNull { (key, value) ->
            val actual = when (key) {
                "ext.cocoa" -> m3["onSurface"] // cocoa text = onSurface
                "ext.cocoaMuted" -> m3["onSurfaceVariant"]
                else -> if (key.startsWith("ext.")) extKotlin[key.removePrefix("ext.")] else m3[key]
            }
            when {
                actual == null -> "$mode.$key missing in Kotlin"
                !hex(actual).equals(value, ignoreCase = true) -> "$mode.$key: tokens $value, Kotlin ${hex(actual)}"
                else -> null
            }
        }
        assertEquals(emptyList(), mismatches)
    }

    @Test fun lightMatchesTokens() = check("light", LightColors, LightSarkari)

    @Test fun darkMatchesTokens() = check("dark", DarkColors, DarkSarkari)
}
