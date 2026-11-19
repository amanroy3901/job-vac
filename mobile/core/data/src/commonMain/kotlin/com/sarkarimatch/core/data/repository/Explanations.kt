package com.sarkarimatch.core.data.repository

import com.sarkarimatch.core.eligibility.LineResult
import com.sarkarimatch.core.eligibility.ReasonCode
import com.sarkarimatch.core.eligibility.RuleKind
import com.sarkarimatch.core.eligibility.RuleLine
import com.sarkarimatch.core.model.LocalizedText
import com.sarkarimatch.core.model.SmFormat

/**
 * Renders "why you match" lines (matching-engine.md §6) as hi + en text from the evaluator's reason codes and
 * computed params. TODO(FE-17): move the templates to Compose string resources (all 13 languages, plurals).
 */
object Explanations {
    @Suppress("LongMethod", "CyclomaticComplexMethod") // one template per ReasonCode; a flat table reads best
    fun render(line: RuleLine, qualificationText: LocalizedText?): LocalizedText? {
        val p = line.params
        fun t(hi: String, en: String) = LocalizedText.of("hi" to hi, "en" to en)
        val rulePart = line.text
        return when (line.code) {
            ReasonCode.AGE_OK -> t("उम्र: ${p["age"]} — सीमा ${p["effectiveMax"]} तक", "Age: ${p["age"]} — limit ${p["effectiveMax"]}")
            ReasonCode.AGE_OK_WITH_RELAXATION -> {
                val label = rulePart?.byLanguage
                t(
                    "उम्र: ${p["age"]} (सीमा ${p["max"]}, ${label?.get("hi") ?: ""} +${p["relaxationYears"]} = ${p["effectiveMax"]})",
                    "Age: ${p["age"]} (limit ${p["max"]}, ${label?.get("en") ?: ""} +${p["relaxationYears"]} = ${p["effectiveMax"]})",
                )
            }
            ReasonCode.AGE_TOO_OLD -> t(
                "उम्र: ${p["age"]} — सीमा ${p["effectiveMax"]} से ज़्यादा",
                "Age: ${p["age"]} — above the limit of ${p["effectiveMax"]}",
            )
            ReasonCode.AGE_TOO_YOUNG -> t(
                "उम्र: ${p["age"]} — कम से कम ${p["min"]} चाहिए",
                "Age: ${p["age"]} — at least ${p["min"]} needed",
            )
            ReasonCode.AGE_DEPENDS_ON_SERVICE -> t("उम्र: सेवा अवधि घटाकर — ख़ुद जाँचें", "Age: after deducting service — check yourself")
            ReasonCode.AGE_NO_LIMIT_FOR_PROFILE -> t("उम्र: आपके लिए सीमा नोटिस में देखें", "Age: check the limit for you in the notice")
            ReasonCode.AGE_CATEGORY_UNSURE -> t(
                "उम्र: वर्ग पक्का होने पर ही छूट मिलेगी",
                "Age: relaxation only with a category certificate",
            )
            ReasonCode.QUAL_OK, ReasonCode.QUAL_OK_HIGHER, ReasonCode.QUAL_PROVISIONAL -> prefixed(
                "पढ़ाई",
                "Qualification",
                qualificationText,
                " — आपके पास है",
                " — you have it",
            )
            ReasonCode.QUAL_MISSING -> prefixed("पढ़ाई", "Qualification", qualificationText, " — आपके पास नहीं", " — not in your profile")
            ReasonCode.QUAL_APPEARING_NOT_ALLOWED -> t(
                "पढ़ाई: अंतिम वर्ष वाले आवेदन नहीं कर सकते",
                "Qualification: final-year students cannot apply",
            )
            ReasonCode.QUAL_RESULT_AFTER_DEADLINE -> t("पढ़ाई: रिज़ल्ट आख़िरी तारीख़ के बाद", "Qualification: result after the deadline")
            ReasonCode.QUAL_APPEARING_CHECK, ReasonCode.QUAL_EQUIVALENT_CHECK, ReasonCode.QUAL_PERCENTAGE_UNKNOWN ->
                prefixed("पढ़ाई", "Qualification", qualificationText, " — ख़ुद जाँचें", " — check yourself")
            ReasonCode.QUAL_PERCENTAGE_LOW -> t("पढ़ाई: अंक कम हैं", "Qualification: marks below the minimum")
            ReasonCode.ATTR_UNDECLARED -> t(
                "${codes(p["codes"], true)}: आपकी प्रोफ़ाइल में नहीं है, ख़ुद जाँचें",
                "${codes(p["codes"], false)}: not in your profile, check yourself",
            )
            ReasonCode.ATTR_MISSING -> t(
                "${codes(p["codes"], true)}: ज़रूरी है, आपके पास नहीं",
                "${codes(p["codes"], false)}: required, you don't have it",
            )
            ReasonCode.ATTR_OK -> t("${codes(p["codes"], true)}: आपके पास है", "${codes(p["codes"], false)}: you have it")
            ReasonCode.ATTR_ADVISORY -> t("जॉइनिंग से पहले: ${codes(p["codes"], true)}", "Before joining: ${codes(p["codes"], false)}")
            ReasonCode.GENDER_OK -> t("लिंग: आपके लिए खुला है", "Gender: open to you")
            ReasonCode.GENDER_NOT_ALLOWED -> t("लिंग: यह पद आपके लिए नहीं है", "Gender: not open to you")
            ReasonCode.GENDER_UNKNOWN -> t("लिंग: प्रोफ़ाइल में जोड़ें", "Gender: add it to your profile")
            ReasonCode.OBC_NCL_ADVISORY -> t(
                "वर्ग: OBC (नॉन-क्रीमी लेयर) — केंद्र की सूची",
                "Category: OBC (non-creamy layer) — central list",
            )
            ReasonCode.CATEGORY_OK -> t("वर्ग: आपके लिए खुला है", "Category: open to you")
            ReasonCode.CATEGORY_NOT_ALLOWED -> t("वर्ग: यह पद आपके वर्ग के लिए नहीं", "Category: not open to your category")
            ReasonCode.DOMICILE_OK -> t("राज्य: आप यहाँ के निवासी हैं", "State: you are a resident")
            ReasonCode.DOMICILE_NOT_ALLOWED -> t("राज्य: केवल ${p["states"]} के निवासी", "State: residents of ${p["states"]} only")
            ReasonCode.DOMICILE_UNKNOWN -> t("राज्य: प्रोफ़ाइल में जोड़ें", "State: add it to your profile")
            ReasonCode.PWD_NOT_ELIGIBLE -> t("दिव्यांग उम्मीदवार इस पद के लिए पात्र नहीं", "Not open to persons with disabilities")
            ReasonCode.PWD_TYPE_NOT_SUITABLE -> t(
                "दिव्यांगता का प्रकार इस पद के लिए उपयुक्त नहीं",
                "Disability type not suitable for this post",
            )
            ReasonCode.PWD_TYPE_UNKNOWN -> t("दिव्यांगता का प्रकार — ख़ुद जाँचें", "Disability type — check yourself")
            ReasonCode.PWD_OK -> null
            ReasonCode.ESM_NOT_ELIGIBLE -> t("पूर्व सैनिक इस पद के लिए पात्र नहीं", "Not open to ex-servicemen")
            ReasonCode.MARITAL_OK -> t("वैवाहिक स्थिति: ठीक है", "Marital status: fine")
            ReasonCode.MARITAL_NOT_ALLOWED -> t("वैवाहिक स्थिति: केवल अविवाहित", "Marital status: unmarried only")
            ReasonCode.MARITAL_UNKNOWN -> t(
                "वैवाहिक स्थिति: केवल अविवाहित — ख़ुद जाँचें",
                "Marital status: unmarried only — check yourself",
            )
            ReasonCode.PHYSICAL_ADVISORY -> prefixed("शारीरिक मानक", "Physical standards", rulePart, "", "")
            ReasonCode.OTHER_CHECK, ReasonCode.OTHER_ADVISORY -> rulePart
            ReasonCode.FEE -> p["paise"]?.toLongOrNull()?.let {
                if (it == 0L) t("फ़ीस: नहीं लगेगी", "Fee: none for you") else t(
                    "फ़ीस: ${SmFormat.rupees(it)}",
                    "Fee: ${SmFormat.rupees(it)}",
                )
            }
            ReasonCode.NEEDS_APP_UPDATE -> t("ऐप अपडेट करें — तभी यह नियम जाँच पाएँगे", "Update the app to check this rule")
        }
    }

    /** The one line shown on a feed card: the deciding line (first non-pass), else the age line. */
    fun headline(lines: List<RuleLine>): RuleLine? =
        lines.firstOrNull { it.result == LineResult.FAIL } ?: lines.firstOrNull { it.result == LineResult.CHECK }
            ?: lines.firstOrNull { it.rule == RuleKind.AGE }

    private fun prefixed(hi: String, en: String, text: LocalizedText?, hiSuffix: String, enSuffix: String): LocalizedText =
        LocalizedText.of(
            "hi" to "$hi: ${text?.byLanguage?.get("hi") ?: text?.byLanguage?.get("en").orEmpty()}$hiSuffix",
            "en" to "$en: ${text?.byLanguage?.get("en").orEmpty()}$enSuffix",
        )

    private val ATTRS = mapOf(
        "ccc" to ("CCC प्रमाणपत्र" to "CCC certificate"),
        "typing_hi" to ("हिंदी टाइपिंग" to "Hindi typing"),
        "typing_en" to ("अंग्रेज़ी टाइपिंग" to "English typing"),
        "ncc_b" to ("NCC 'B'" to "NCC 'B'"),
        "ncc_c" to ("NCC 'C'" to "NCC 'C'"),
        "ctet" to ("CTET" to "CTET"),
        "uptet" to ("UPTET" to "UPTET"),
        "driving_licence_lmv" to ("ड्राइविंग लाइसेंस" to "Driving licence"),
    )

    private fun codes(csv: String?, hi: Boolean): String = csv.orEmpty().split(',').filter { it.isNotBlank() }
        .joinToString(if (hi) " या " else " or ") { c -> ATTRS[c]?.let { if (hi) it.first else it.second } ?: c }
}
