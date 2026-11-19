package com.sarkarimatch.core.model

/** Writing systems; each has its own font chain and line-height multiplier (docs/frontend/i18n-and-design-system.md A3). */
enum class Script(val lineHeightMultiplier: Float) {
    Latin(1.0f),
    Devanagari(1.0f),
    Bengali(1.05f),
    Telugu(1.15f),
    Tamil(1.05f),
    Gujarati(1.0f),
    Kannada(1.15f),
    Odia(1.15f),
    Malayalam(1.1f),
    Gurmukhi(1.0f),
    Arabic(1.6f),
}

/**
 * The 13 languages the architecture supports (founder decision 1). Only [launch] ones are enabled in this build;
 * the rest are "coming soon" until their resources are 100 % reviewed.
 *
 * @property tag BCP-47 language code, also the key in [LocalizedText].
 * @property endonym the language's name in its own script (shown on S02/S40 regardless of app language).
 * @property fallback chain used by [LocalizedText.pick] before `en`.
 */
enum class AppLanguage(
    val tag: String,
    val endonym: String,
    val script: Script,
    val rtl: Boolean = false,
    val launch: Boolean = false,
    val fallback: List<String> = emptyList(),
) {
    HI("hi", "हिंदी", Script.Devanagari, launch = true),
    EN("en", "English", Script.Latin, launch = true),
    BN("bn", "বাংলা", Script.Bengali),
    MR("mr", "मराठी", Script.Devanagari, fallback = listOf("hi")),
    TE("te", "తెలుగు", Script.Telugu),
    TA("ta", "தமிழ்", Script.Tamil),
    GU("gu", "ગુજરાતી", Script.Gujarati, fallback = listOf("hi")),
    KN("kn", "ಕನ್ನಡ", Script.Kannada),
    OR("or", "ଓଡ଼ିଆ", Script.Odia),
    ML("ml", "മലയാളം", Script.Malayalam),
    PA("pa", "ਪੰਜਾਬੀ", Script.Gurmukhi, fallback = listOf("hi")),
    AS("as", "অসমীয়া", Script.Bengali, fallback = listOf("bn")),
    UR("ur", "اردو", Script.Arabic, rtl = true),
    ;

    companion object {
        /** Hindi is the default for every new user (i18n A1). */
        val DEFAULT = HI

        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag?.substringBefore('-') }

        val launched: List<AppLanguage> get() = entries.filter { it.launch }
    }
}
