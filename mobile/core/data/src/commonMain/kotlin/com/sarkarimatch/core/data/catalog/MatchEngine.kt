package com.sarkarimatch.core.data.catalog

import com.sarkarimatch.core.eligibility.EligibilityEvaluator
import com.sarkarimatch.core.eligibility.EligibilityProfile
import com.sarkarimatch.core.eligibility.Evaluation
import com.sarkarimatch.core.eligibility.RuleJson

/**
 * Adapter between the catalog in Room and the on-device evaluator (:core:eligibility). Matching happens on the
 * phone; the profile never leaves it (matching-engine.md §0, ADR-11).
 */
class MatchEngine(private val evaluator: EligibilityEvaluator) {
    fun evaluate(ruleJson: String, profile: EligibilityProfile): Evaluation =
        evaluator.evaluate(RuleJson.decode(ruleJson), profile)
}
