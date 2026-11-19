package com.sarkarimatch.core.eligibility

import com.sarkarimatch.core.eligibility.Fixtures.profile
import com.sarkarimatch.core.model.Category
import com.sarkarimatch.core.model.Gender
import com.sarkarimatch.core.model.QualificationStatus
import com.sarkarimatch.core.model.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

/** SSC GD 2026: cutoff 01-01-2026, 18–23 not attained, OBC +3, SC/ST +5, ESM after service deduction. */
class SscGdEvaluationTest {
    private val evaluator = EligibilityEvaluator()
    private val rule = Fixtures.sscGd
    private val ba = listOf(HeldQualification("grad.ba", QualificationStatus.COMPLETED))

    private fun age(p: EligibilityProfile) = evaluator.evaluate(rule, p).lines.first { it.rule == RuleKind.AGE }

    @Test
    fun raviOBCNCL24OnCutoffBAEligibleWithOBCRelaxationAnd100RupeeFee() {
        val ravi = profile("2001-05-10", category = Category.OBC, quals = ba)
        val result = evaluator.evaluate(rule, ravi)
        assertEquals(Verdict.ELIGIBLE, result.verdict)
        val ageLine = age(ravi)
        assertEquals(ReasonCode.AGE_OK_WITH_RELAXATION, ageLine.code)
        assertEquals("24", ageLine.params["age"])
        assertEquals("obc", ageLine.params["relaxation"])
        assertEquals("26", ageLine.params["effectiveMax"])
        assertEquals(ReasonCode.QUAL_OK_HIGHER, result.lines.first { it.rule == RuleKind.QUALIFICATION }.code)
        assertEquals(10_000L, result.feePaise)
    }

    @Test
    fun boundariesOfTheBaseWindowForGeneralCategory() {
        assertEquals(LineResult.PASS, age(profile("2003-01-02")).result) // lo
        assertEquals(ReasonCode.AGE_TOO_OLD, age(profile("2003-01-01")).code) // lo - 1d
        assertEquals(LineResult.PASS, age(profile("2008-01-01")).result) // hi
        assertEquals(ReasonCode.AGE_TOO_YOUNG, age(profile("2008-01-02")).code) // hi + 1d
    }

    @Test
    fun categoryRelaxationSameDOBFailsForGENPassesForOBCSC() {
        val dob = "2000-06-15" // 25 on cutoff
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(rule, profile(dob)).verdict)
        assertEquals(Verdict.ELIGIBLE, evaluator.evaluate(rule, profile(dob, category = Category.OBC)).verdict)
        assertEquals(Verdict.ELIGIBLE, evaluator.evaluate(rule, profile(dob, category = Category.SC)).verdict)
    }

    @Test
    fun oBCCreamyLayerIsTreatedAsGEN() {
        val p = profile("2000-06-15", category = Category.OBC, obcNcl = false)
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(rule, p).verdict)
    }

    @Test
    fun sCBoundary28NotAttained() {
        assertEquals(LineResult.PASS, age(profile("1998-01-02", category = Category.SC)).result)
        assertEquals(LineResult.FAIL, age(profile("1998-01-01", category = Category.SC)).result)
    }

    @Test
    fun eSMOBCWith96MonthsServicePicksTheMostBeneficialRow() {
        val p = profile("1996-06-01", category = Category.OBC, esm = true, esmServiceMonths = 96)
        val line = age(p)
        assertEquals(LineResult.PASS, line.result)
        assertEquals("esm_obc", line.params["relaxation"])
        assertEquals(Verdict.ELIGIBLE, evaluator.evaluate(rule, p).verdict)
        assertEquals(0L, evaluator.evaluate(rule, p).feePaise) // ESM fee exemption
    }

    @Test
    fun eSMWithUnknownServiceMonthsIsCheckYourselfNeverAGuess() {
        val p = profile("1996-06-01", category = Category.OBC, esm = true, esmServiceMonths = null)
        assertEquals(ReasonCode.AGE_DEPENDS_ON_SERVICE, age(p).code)
        assertEquals(Verdict.CHECK_YOURSELF, evaluator.evaluate(rule, p).verdict)
    }

    @Test
    fun riotVictimRowsAreNeverAppliedFactNotCollected() {
        // 27 on cutoff: would pass only with riot_ur (+5); must not match.
        val p = profile("1998-06-01")
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(rule, p).verdict)
    }

    @Test
    fun categoryUnsureFailsForGENButWouldPassForOBCBecomesCheckYourself() {
        val p = profile("2000-06-15", categoryUnsure = true)
        assertEquals(ReasonCode.AGE_CATEGORY_UNSURE, age(p).code)
        assertEquals(Verdict.CHECK_YOURSELF, evaluator.evaluate(rule, p).verdict)
    }

    @Test
    fun qualificationNoneAppearingWithUnknownResultCompleted10th() {
        val noQual = profile("2004-01-01", quals = listOf(HeldQualification("class8", QualificationStatus.COMPLETED)))
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(rule, noQual).verdict)

        val appearing = profile("2007-01-01", quals = listOf(HeldQualification("class10", QualificationStatus.APPEARING)))
        assertEquals(Verdict.CHECK_YOURSELF, evaluator.evaluate(rule, appearing).verdict)

        val tenth = profile("2007-01-01", quals = listOf(HeldQualification("class10", QualificationStatus.COMPLETED)))
        assertEquals(Verdict.ELIGIBLE, evaluator.evaluate(rule, tenth).verdict)
    }

    @Test
    fun pwDCandidatesAreNotEligible() {
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(rule, profile("2004-01-01", pwd = true)).verdict)
    }

    @Test
    fun feeIsZeroForWomenAndSC100Otherwise() {
        assertEquals(0L, evaluator.evaluate(rule, profile("2004-01-01", gender = Gender.FEMALE)).feePaise)
        assertEquals(0L, evaluator.evaluate(rule, profile("2004-01-01", category = Category.SC)).feePaise)
        assertEquals(10_000L, evaluator.evaluate(rule, profile("2004-01-01")).feePaise)
    }

    @Test
    fun skippedGenderIsCheckYourself() {
        assertEquals(Verdict.CHECK_YOURSELF, evaluator.evaluate(rule, profile("2004-01-01", gender = null)).verdict)
    }

    @Test
    fun aRuleNeedingANewerEvaluatorIsCheckYourselfWithUpdateLine() {
        val future = rule.copy(evaluatorMinVersion = EligibilityEvaluator.EVALUATOR_VERSION + 1)
        val result = evaluator.evaluate(future, profile("2004-01-01"))
        assertEquals(Verdict.CHECK_YOURSELF, result.verdict)
        assertEquals(ReasonCode.NEEDS_APP_UPDATE, result.lines.single { it.rule == RuleKind.EVALUATOR }.code)
    }
}
