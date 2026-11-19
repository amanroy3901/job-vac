package com.sarkarimatch.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Server-provided text per language (`LocalizedText` in rule.schema.json / openapi.yaml). Unknown language keys are
 * kept so languages added later still round-trip.
 */
@Serializable(with = LocalizedTextSerializer::class)
data class LocalizedText(val byLanguage: Map<String, String>) {
    /** Walks `[lang] + lang.fallback + en`, then any value. */
    fun pick(language: AppLanguage): String {
        val chain = listOf(language.tag) + language.fallback + "en"
        return chain.firstNotNullOfOrNull { byLanguage[it] } ?: byLanguage.values.firstOrNull().orEmpty()
    }

    companion object {
        fun of(vararg pairs: Pair<String, String>) = LocalizedText(mapOf(*pairs))
    }
}

object LocalizedTextSerializer : KSerializer<LocalizedText> {
    private val delegate = MapSerializer(String.serializer(), String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: LocalizedText) = delegate.serialize(encoder, value.byLanguage)
    override fun deserialize(decoder: Decoder) = LocalizedText(delegate.deserialize(decoder))
}
