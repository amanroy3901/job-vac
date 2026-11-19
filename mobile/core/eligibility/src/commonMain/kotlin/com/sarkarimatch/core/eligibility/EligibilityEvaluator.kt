package com.sarkarimatch.core.eligibility

import com.sarkarimatch.core.model.Category
import com.sarkarimatch.core.model.Gender
import com.sarkarimatch.core.model.LocalizedText
import com.sarkarimatch.core.model.PercentageBand
import com.sarkarimatch.core.model.QualificationStatus
import com.sarkarimatch.core.model.Verdict
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearsUntil

/**
 * Deterministic on-device evaluator for rule.schema.json v1 (docs/backend/matching-engine.md §3).
 *
 * Implemented: evaluator version gate, age on the cutoff date (both max conventions, general extension, the single
 * most beneficial relaxation row, ESM service deduction, needs_profile handling, category-unsure), qualification
 * (codes/levels/higher, must-complete-by, appearing policy, percentage band, "or equivalent"), required attributes,
 * domicile, gender, category, PwD, ESM, marital status, physical/other advisories and the fee row.
 */
class EligibilityEvaluator(
    private val taxonomy: QualificationTaxonomy = QualificationTaxonomy.Builtin,
) {
    fun evaluate(rule: RuleDocument, profile: EligibilityProfile): Evaluation {
        if (rule.evaluatorMinVersion > EVALUATOR_VERSION) {
            val line = RuleLine(RuleKind.EVALUATOR, LineResult.CHECK, ReasonCode.NEEDS_APP_UPDATE)
            return Evaluation(Verdict.CHECK_YOURSELF, listOf(line), feeFor(rule, profile))
        }
        val lines = buildList {
            add(evaluateAge(rule.age, profile))
            if (profile.category == Category.OBC && profile.obcNonCreamyLayer == null) {
                add(RuleLine(RuleKind.CATEGORY, LineResult.INFO, ReasonCode.OBC_NCL_ADVISORY))
            }
            add(evaluateQualification(rule.qualification, profile))
            addAll(evaluateAttributes(rule.requiredAttributes, profile))
            rule.domicile?.let { addAll(evaluateDomicile(it, profile)) }
            rule.gender?.allowed?.let { add(evaluateGender(it, profile)) }
            rule.category?.allowed?.let { add(evaluateCategory(it, profile)) }
            rule.pwd?.let { evaluatePwd(it, profile)?.let(::add) }
            rule.exServiceman?.let { if (!it.eligible && profile.esm) add(fail(RuleKind.ESM, ReasonCode.ESM_NOT_ELIGIBLE)) }
            rule.maritalStatus?.allowed?.let { add(evaluateMarital(it, profile)) }
            rule.physical?.text?.let { add(RuleLine(RuleKind.PHYSICAL, LineResult.INFO, ReasonCode.PHYSICAL_ADVISORY, text = it)) }
            rule.otherConditions.filter { ConditionMatcher.matches(it.`when`, profile) }.forEach {
                val check = it.severity == Severity.CHECK_YOURSELF
                add(
                    RuleLine(
                        RuleKind.OTHER,
                        if (check) LineResult.CHECK else LineResult.INFO,
                        if (check) ReasonCode.OTHER_CHECK else ReasonCode.OTHER_ADVISORY,
                        text = it.text,
                    ),
                )
            }
        }
        val verdict = when {
            lines.any { it.result == LineResult.FAIL } -> Verdict.NOT_ELIGIBLE
            lines.any { it.result == LineResult.CHECK } -> Verdict.CHECK_YOURSELF
            else -> Verdict.ELIGIBLE
        }
        val fee = feeFor(rule, profile)
        val feeLine = fee?.let { RuleLine(RuleKind.FEE, LineResult.INFO, ReasonCode.FEE, mapOf("paise" to it.toString())) }
        return Evaluation(verdict, lines + listOfNotNull(feeLine), fee)
    }

    /** Targeting (matching-engine.md §2): interests overlap and scope fits. Not eligibility. */
    fun isTargeted(rule: RuleDocument, profile: EligibilityProfile): Boolean {
        val interested = profile.interests.isEmpty() || rule.examTags.any { it in profile.interests }
        val scope = rule.scope
        val inScope = scope == null || scope.kind == "all_india" ||
            scope.state == null || scope.state == profile.domicile || scope.state in profile.jobStates
        return interested && inScope
    }

    /** First fee row whose `when` matches; a row without `when` is the default. */
    fun feeFor(rule: RuleDocument, profile: EligibilityProfile): Long? =
        rule.fees.firstOrNull { ConditionMatcher.matches(it.`when`, profile) }?.amountPaise

    // ---------------------------------------------------------------- age (§3.1)

    internal fun evaluateAge(age: AgeRule, p: EligibilityProfile): RuleLine {
        val primary = evaluateAgeFor(age, p, p.effectiveCategory)
        if (primary.result == LineResult.FAIL && p.categoryUnsure && primary.code == ReasonCode.AGE_TOO_OLD) {
            val wouldPass = listOf(Category.OBC, Category.SC, Category.ST).any {
                evaluateAgeFor(age, p, it).result == LineResult.PASS
            }
            if (wouldPass) return primary.copy(result = LineResult.CHECK, code = ReasonCode.AGE_CATEGORY_UNSURE)
        }
        return primary
    }

    private fun evaluateAgeFor(age: AgeRule, p: EligibilityProfile, category: Category): RuleLine {
        val cutoff = LocalDate.parse(age.cutoffDate)
        val ageOnCutoff = p.dob.yearsUntil(cutoff)
        val baseParams = mapOf("age" to ageOnCutoff.toString(), "cutoff" to age.cutoffDate)
        val limit = age.limits.firstOrNull { ConditionMatcher.matches(it.`when`, p, category) }
            ?: return RuleLine(RuleKind.AGE, LineResult.CHECK, ReasonCode.AGE_NO_LIMIT_FOR_PROFILE, baseParams)

        val g = age.generalExtensionYears
        val latestDob = cutoff.minus(limit.minYears, DateTimeUnit.YEAR)
        if (p.dob > latestDob) {
            return RuleLine(
                RuleKind.AGE,
                LineResult.FAIL,
                ReasonCode.AGE_TOO_YOUNG,
                baseParams + mapOf("min" to limit.minYears.toString(), "bornOnOrBefore" to latestDob.toString()),
            )
        }

        // Base window (no relaxation) and every applicable relaxation; the earliest DOB bound wins.
        data class Bound(val earliestDob: LocalDate, val relaxation: AgeRelaxation?, val maxYears: Int)
        val bounds = mutableListOf(Bound(earliestDob(cutoff, limit.maxYears + g, 0, age.maxConvention), null, limit.maxYears + g))
        var pendingServiceCheck: AgeRelaxation? = null
        for (r in age.relaxations) {
            if (!ConditionMatcher.matches(r.`when`, p, category)) continue
            if (r.needsProfile.any { !it.collected }) continue // never collected → excluded from matching
            val needsService = ProfileFact.ESM_SERVICE_MONTHS in r.needsProfile ||
                r.mode == RelaxationMode.AFTER_SERVICE_DEDUCTION
            val needsGovt = ProfileFact.GOVT_EMPLOYEE in r.needsProfile
            if ((needsService && p.esmServiceMonths == null) || (needsGovt && p.govtEmployee == null)) {
                pendingServiceCheck = pendingServiceCheck ?: r
                continue
            }
            val years = (limit.maxYears + g + r.years).let { m -> r.absoluteMaxYears?.let { minOf(m, it) } ?: m }
            val serviceMonths = if (r.mode == RelaxationMode.AFTER_SERVICE_DEDUCTION) p.esmServiceMonths ?: 0 else 0
            bounds += Bound(earliestDob(cutoff, years, serviceMonths, age.maxConvention), r, years)
        }
        val best = bounds.minBy { it.earliestDob }
        val params = baseParams + mapOf(
            "max" to limit.maxYears.toString(),
            "general" to g.toString(),
            "effectiveMax" to best.maxYears.toString(),
            "bornOnOrAfter" to best.earliestDob.toString(),
        ) + (best.relaxation?.let { mapOf("relaxation" to it.id, "relaxationYears" to it.years.toString()) } ?: emptyMap())

        return when {
            p.dob >= best.earliestDob -> RuleLine(
                RuleKind.AGE,
                LineResult.PASS,
                if (best.relaxation != null) ReasonCode.AGE_OK_WITH_RELAXATION else ReasonCode.AGE_OK,
                params,
                best.relaxation?.label,
            )
            pendingServiceCheck != null -> RuleLine(
                RuleKind.AGE,
                LineResult.CHECK,
                ReasonCode.AGE_DEPENDS_ON_SERVICE,
                params + ("relaxation" to pendingServiceCheck.id),
                pendingServiceCheck.label,
            )
            else -> RuleLine(RuleKind.AGE, LineResult.FAIL, ReasonCode.AGE_TOO_OLD, params)
        }
    }

    /**
     * Earliest allowed DOB for an upper limit of [maxYears]:
     * not_attained: `c − M years − s + 1 day`; completed_years: `c − (M + 1) years − s + 1 day`.
     */
    internal fun earliestDob(cutoff: LocalDate, maxYears: Int, serviceMonths: Int, convention: MaxConvention): LocalDate {
        val years = if (convention == MaxConvention.COMPLETED_YEARS) maxYears + 1 else maxYears
        return cutoff.minus(years, DateTimeUnit.YEAR).minus(serviceMonths, DateTimeUnit.MONTH).plus(1, DateTimeUnit.DAY)
    }

    /** The unrelaxed window the notice prints ("born not before X and not later than Y"), for the publish validator. */
    fun baseDobWindow(age: AgeRule, limit: AgeLimit): Pair<LocalDate, LocalDate> {
        val cutoff = LocalDate.parse(age.cutoffDate)
        return earliestDob(cutoff, limit.maxYears, 0, age.maxConvention) to cutoff.minus(limit.minYears, DateTimeUnit.YEAR)
    }

    // ---------------------------------------------------------------- qualification (§3.2)

    internal fun evaluateQualification(q: QualificationRule, p: EligibilityProfile): RuleLine {
        val mustCompleteBy = q.mustCompleteBy?.let { LocalDate.parse(it) }
        val candidates = q.anyOf.filter { ConditionMatcher.matches(it.`when`, p) }.flatMap { alt ->
            p.qualifications.map { held -> evaluateAlternative(alt, held, q, mustCompleteBy) }
        }
        val best = candidates.maxByOrNull { rank(it.result) }
            ?: return fail(RuleKind.QUALIFICATION, ReasonCode.QUAL_MISSING, text = q.text)
        return best.copy(text = q.text)
    }

    private fun evaluateAlternative(
        alt: QualificationAlternative,
        held: HeldQualification,
        q: QualificationRule,
        mustCompleteBy: LocalDate?,
    ): RuleLine {
        val heldCodes = taxonomy.selfAndAncestors(held.code)
        val heldLevel = taxonomy.level(held.code)
        val listedLevels = alt.codes.mapNotNull { taxonomy.level(it) }
        val requiredLevel = alt.minLevel ?: listedLevels.maxOrNull()
        val params = mapOf("held" to held.code)

        val direct = heldCodes.any { it in alt.codes } || (alt.minLevel != null && heldLevel != null && heldLevel >= alt.minLevel)
        val higher = !direct && alt.includeHigher && heldLevel != null && requiredLevel != null && heldLevel > requiredLevel
        val sameLevelUnlisted = !direct && !higher && heldLevel != null && heldLevel == requiredLevel

        if (!direct && !higher) {
            return if (sameLevelUnlisted && q.equivalence == Equivalence.CHECK_YOURSELF_SAME_LEVEL) {
                RuleLine(RuleKind.QUALIFICATION, LineResult.CHECK, ReasonCode.QUAL_EQUIVALENT_CHECK, params)
            } else {
                fail(RuleKind.QUALIFICATION, ReasonCode.QUAL_MISSING, params)
            }
        }

        // Percentage band (only meaningful for the listed level).
        alt.minPercentageBand?.let { bandName ->
            val needed = PercentageBand.entries.firstOrNull { it.serialName() == bandName }
            val have = held.percentageBand
            if (needed != null && !higher) {
                if (have == null) return RuleLine(RuleKind.QUALIFICATION, LineResult.CHECK, ReasonCode.QUAL_PERCENTAGE_UNKNOWN, params)
                if (have < needed) return fail(RuleKind.QUALIFICATION, ReasonCode.QUAL_PERCENTAGE_LOW, params)
            }
        }

        val okCode = if (higher) ReasonCode.QUAL_OK_HIGHER else ReasonCode.QUAL_OK
        return when (held.status) {
            QualificationStatus.COMPLETED ->
                if (mustCompleteBy != null && held.resultDate != null && held.resultDate > mustCompleteBy) {
                    fail(RuleKind.QUALIFICATION, ReasonCode.QUAL_RESULT_AFTER_DEADLINE, params)
                } else {
                    RuleLine(RuleKind.QUALIFICATION, LineResult.PASS, okCode, params)
                }
            QualificationStatus.FINAL_YEAR, QualificationStatus.APPEARING -> when (q.appearingPolicy) {
                AppearingPolicy.ALLOWED -> RuleLine(RuleKind.QUALIFICATION, LineResult.PASS, ReasonCode.QUAL_PROVISIONAL, params)
                AppearingPolicy.NOT_ALLOWED -> fail(RuleKind.QUALIFICATION, ReasonCode.QUAL_APPEARING_NOT_ALLOWED, params)
                AppearingPolicy.IF_PASSED_BY_DATE ->
                    if (mustCompleteBy != null && held.resultDate != null && held.resultDate <= mustCompleteBy) {
                        RuleLine(RuleKind.QUALIFICATION, LineResult.PASS, ReasonCode.QUAL_PROVISIONAL, params)
                    } else {
                        RuleLine(RuleKind.QUALIFICATION, LineResult.CHECK, ReasonCode.QUAL_APPEARING_CHECK, params)
                    }
            }
        }
    }

    // ---------------------------------------------------------------- other rules (§3.3)

    private fun evaluateAttributes(attrs: List<RequiredAttribute>, p: EligibilityProfile): List<RuleLine> = attrs.map { a ->
        val params = mapOf("codes" to a.anyOfCodes.joinToString(","))
        when {
            a.stage != AttributeStage.AT_APPLICATION -> RuleLine(RuleKind.ATTRIBUTES, LineResult.INFO, ReasonCode.ATTR_ADVISORY, params)
            a.anyOfCodes.any { it in p.extras } -> RuleLine(RuleKind.ATTRIBUTES, LineResult.PASS, ReasonCode.ATTR_OK, params)
            p.extrasDeclared -> fail(RuleKind.ATTRIBUTES, ReasonCode.ATTR_MISSING, params)
            else -> RuleLine(RuleKind.ATTRIBUTES, LineResult.CHECK, ReasonCode.ATTR_UNDECLARED, params)
        }
    }

    private fun evaluateDomicile(d: DomicileRule, p: EligibilityProfile): List<RuleLine> = buildList {
        val required = d.requiredStates
        if (!required.isNullOrEmpty()) {
            val params = mapOf("states" to required.joinToString(","))
            add(
                when {
                    p.domicile == null -> RuleLine(RuleKind.DOMICILE, LineResult.CHECK, ReasonCode.DOMICILE_UNKNOWN, params)
                    p.domicile in required -> RuleLine(RuleKind.DOMICILE, LineResult.PASS, ReasonCode.DOMICILE_OK, params)
                    else -> fail(RuleKind.DOMICILE, ReasonCode.DOMICILE_NOT_ALLOWED, params)
                },
            )
        }
        d.note?.let { add(RuleLine(RuleKind.DOMICILE, LineResult.INFO, ReasonCode.OTHER_ADVISORY, text = it)) }
    }

    private fun evaluateGender(allowed: List<Gender>, p: EligibilityProfile): RuleLine = when {
        p.gender == null -> RuleLine(RuleKind.GENDER, LineResult.CHECK, ReasonCode.GENDER_UNKNOWN)
        p.gender in allowed ->
            RuleLine(RuleKind.GENDER, LineResult.PASS, ReasonCode.GENDER_OK, mapOf("allowed" to allowed.joinToString(",")))
        else -> fail(RuleKind.GENDER, ReasonCode.GENDER_NOT_ALLOWED)
    }

    private fun evaluateCategory(allowed: List<Category>, p: EligibilityProfile): RuleLine =
        if (p.effectiveCategory in allowed) {
            RuleLine(RuleKind.CATEGORY, LineResult.PASS, ReasonCode.CATEGORY_OK)
        } else {
            fail(RuleKind.CATEGORY, ReasonCode.CATEGORY_NOT_ALLOWED)
        }

    private fun evaluatePwd(rule: PwdRule, p: EligibilityProfile): RuleLine? {
        if (!p.pwd) return null
        if (rule.eligible == false) return fail(RuleKind.PWD, ReasonCode.PWD_NOT_ELIGIBLE)
        val types = rule.suitableTypes ?: return RuleLine(RuleKind.PWD, LineResult.PASS, ReasonCode.PWD_OK)
        return when (p.pwdType) {
            null -> RuleLine(RuleKind.PWD, LineResult.CHECK, ReasonCode.PWD_TYPE_UNKNOWN)
            in types -> RuleLine(RuleKind.PWD, LineResult.PASS, ReasonCode.PWD_OK)
            else -> fail(RuleKind.PWD, ReasonCode.PWD_TYPE_NOT_SUITABLE)
        }
    }

    private fun evaluateMarital(allowed: List<String>, p: EligibilityProfile): RuleLine {
        val m = p.marital ?: return RuleLine(RuleKind.MARITAL, LineResult.CHECK, ReasonCode.MARITAL_UNKNOWN)
        return if (allowed.any { it.equals(m.name, ignoreCase = true) }) {
            RuleLine(RuleKind.MARITAL, LineResult.PASS, ReasonCode.MARITAL_OK)
        } else {
            fail(RuleKind.MARITAL, ReasonCode.MARITAL_NOT_ALLOWED)
        }
    }

    private fun fail(
        kind: RuleKind,
        code: ReasonCode,
        params: Map<String, String> = emptyMap(),
        text: LocalizedText? = null,
    ) = RuleLine(kind, LineResult.FAIL, code, params, text)

    private fun rank(r: LineResult) = when (r) {
        LineResult.PASS -> 3
        LineResult.CHECK -> 2
        LineResult.INFO -> 1
        LineResult.FAIL -> 0
    }

    private fun PercentageBand.serialName(): String = when (this) {
        PercentageBand.LT_50 -> "lt_50"
        PercentageBand.B50_55 -> "50_55"
        PercentageBand.B55_60 -> "55_60"
        PercentageBand.GE_60 -> "ge_60"
    }

    companion object {
        /** Bumped when rule semantics or the schema grow (matching-engine.md §4.3). */
        const val EVALUATOR_VERSION = 1
    }
}
