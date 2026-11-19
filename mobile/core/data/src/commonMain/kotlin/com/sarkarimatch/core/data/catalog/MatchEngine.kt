package com.sarkarimatch.core.data.catalog

import com.sarkarimatch.core.eligibility.EligibilityEvaluator
import com.sarkarimatch.core.eligibility.EligibilityProfile
import com.sarkarimatch.core.eligibility.Evaluation
import com.sarkarimatch.core.eligibility.RuleDocument
import com.sarkarimatch.core.eligibility.RuleJson

/**
 * Adapter between the catalog in Room and the on-device evaluator (:core:eligibility). Matching happens on the
 * phone; the profile never leaves it (matching-engine.md §0, ADR-11).
 */
class MatchEngine(private val evaluator: EligibilityEvaluator) {
    fun decode(ruleJson: String): RuleDocument = RuleJson.decode(ruleJson)

    fun evaluate(ruleJson: String, profile: EligibilityProfile): Evaluation = evaluate(decode(ruleJson), profile)

    fun evaluate(rule: RuleDocument, profile: EligibilityProfile): Evaluation = evaluator.evaluate(rule, profile)

    /** Interests overlap and scope fit (targeting, not eligibility). */
    fun isTargeted(rule: RuleDocument, profile: EligibilityProfile): Boolean = evaluator.isTargeted(rule, profile)
}
