package com.sarkarimatch.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * Every colour role in docs/design/tokens.json must equal the Kotlin theme (i18n-and-design-system.md B1, D11).
 * A token change in design fails CI until Color.kt / SarkariColors.kt are updated, and vice versa.
 */
class TokensParityTest {
    private val tokens: JsonObject? = System.getProperty("sm.tokensJson")?.let(::File)?.takeIf { it.isFile }
        ?.let { Json.parseToJsonElement(it.readText()).jsonObject }

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
        val rgb = "%06X".format(argb and 0xFFFFFF)
        return if (alpha == 0xFFL) "#$rgb" else "#%02X%s".format(alpha, rgb)
    }

    private fun schemeRoles(s: ColorScheme): Map<String, Color> = ColorScheme::class.java.methods
        .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }
        .associate { m -> m.name.removePrefix("get").replaceFirstChar(Char::lowercase).substringBefore('-') to Color(m.invoke(s) as Long) }

    private fun extRoles(s: SarkariColors): Map<String, Color> = buildMap {
        SarkariColors::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }
            .forEach { m ->
                put(m.name.removePrefix("get").replaceFirstChar(Char::lowercase).substringBefore('-'), Color(m.invoke(s) as Long))
            }
        listOf("police" to s.police, "ssc" to s.ssc, "railway" to s.railway, "bank" to s.bank, "state" to s.state)
            .forEach { (name, c) ->
                put("category.$name.container", c.container)
                put("category.$name.icon", c.icon)
            }
    }

    private fun check(mode: String, scheme: ColorScheme, ext: SarkariColors) {
        val json = tokens
        assumeTrue("tokens.json not found", json != null)
        val flat = flatten(json!!["color"]!!.jsonObject[mode]!!.jsonObject)
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
