package com.sarkarimatch.core.eligibility

import com.sarkarimatch.core.model.LocalizedText
import com.sarkarimatch.core.model.Verdict

/** Result of one rule line. INFO lines are advisory and never change the verdict. */
enum class LineResult { PASS, CHECK, FAIL, INFO }

enum class RuleKind { EVALUATOR, AGE, QUALIFICATION, ATTRIBUTES, DOMICILE, GENDER, CATEGORY, PWD, ESM, MARITAL, PHYSICAL, OTHER, FEE }

/**
 * Stable reason codes. Stored per local match (`reason_codes`) and used to pick the string template in the app
 * language, so the ordinal of existing entries must never change: only append.
 */
enum class ReasonCode {
    NEEDS_APP_UPDATE,
    AGE_OK,
    AGE_OK_WITH_RELAXATION,
    AGE_TOO_YOUNG,
    AGE_TOO_OLD,
    AGE_NO_LIMIT_FOR_PROFILE,
    AGE_DEPENDS_ON_SERVICE,
    AGE_CATEGORY_UNSURE,
    QUAL_OK,
    QUAL_OK_HIGHER,
    QUAL_PROVISIONAL,
    QUAL_RESULT_AFTER_DEADLINE,
    QUAL_APPEARING_NOT_ALLOWED,
    QUAL_APPEARING_CHECK,
    QUAL_PERCENTAGE_UNKNOWN,
    QUAL_PERCENTAGE_LOW,
    QUAL_EQUIVALENT_CHECK,
    QUAL_MISSING,
    ATTR_OK,
    ATTR_MISSING,
    ATTR_UNDECLARED,
    ATTR_ADVISORY,
    DOMICILE_OK,
    DOMICILE_NOT_ALLOWED,
    DOMICILE_UNKNOWN,
    GENDER_OK,
    GENDER_NOT_ALLOWED,
    GENDER_UNKNOWN,
    CATEGORY_OK,
    CATEGORY_NOT_ALLOWED,
    PWD_NOT_ELIGIBLE,
    PWD_TYPE_NOT_SUITABLE,
    PWD_TYPE_UNKNOWN,
    PWD_OK,
    ESM_NOT_ELIGIBLE,
    MARITAL_OK,
    MARITAL_NOT_ALLOWED,
    MARITAL_UNKNOWN,
    PHYSICAL_ADVISORY,
    OTHER_CHECK,
    OTHER_ADVISORY,
    OBC_NCL_ADVISORY,
    FEE,
}

/**
 * One "why you match" line (matching-engine.md §6). [params] are computed values (ages, dates, years, relaxation
 * id); the app renders the text from per-language templates keyed by [code]. [text] carries rule-provided text.
 */
data class RuleLine(
    val rule: RuleKind,
    val result: LineResult,
    val code: ReasonCode,
    val params: Map<String, String> = emptyMap(),
    val text: LocalizedText? = null,
)

data class Evaluation(
    val verdict: Verdict,
    val lines: List<RuleLine>,
    /** Fee row for this profile; null when the rule has none matching (never happens for a valid rule). */
    val feePaise: Long?,
) {
    val reasonCodes: List<Int> get() = lines.filter { it.result != LineResult.PASS }.map { it.code.ordinal }
}
