package com.sarkarimatch.core.data.catalog

import com.sarkarimatch.core.eligibility.EligibilityEvaluator
import com.sarkarimatch.core.eligibility.EligibilityProfile
import com.sarkarimatch.core.eligibility.Evaluation
import com.sarkarimatch.core.eligibility.RuleJson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter between the catalog in Room and the on-device evaluator (:core:eligibility). Matching happens on the
 * phone; the profile never leaves it (matching-engine.md §0, ADR-11).
 */
@Singleton
class MatchEngine @Inject constructor(private val evaluator: EligibilityEvaluator) {
    fun evaluate(ruleJson: String, profile: EligibilityProfile): Evaluation =
        evaluator.evaluate(RuleJson.decode(ruleJson), profile)
}
