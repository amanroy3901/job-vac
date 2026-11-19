package com.sarkarimatch.core.eligibility

import com.sarkarimatch.core.eligibility.Fixtures.profile
import com.sarkarimatch.core.model.Category
import com.sarkarimatch.core.model.Gender
import com.sarkarimatch.core.model.QualificationStatus
import com.sarkarimatch.core.model.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UP Police Constable 2025 (rule v2): cutoff 01-07-2025, male 18–22 / female 18–25, one-time +3 for all,
 * OBC/SC/ST +5 only for UP domicile, 12th pass (appearing not allowed). Examples from matching-engine.md §6.
 */
class UpPoliceEvaluationTest {
    private val evaluator = EligibilityEvaluator()
    private val v2 = Fixtures.upPolice
    private val v1 = v2.copy(age = v2.age.copy(generalExtensionYears = 0))

    @Test
    fun amitMaleGENUP23OnCutoffNotEligibleOnV1EligibleOnV2() {
        val amit = profile("2001-09-15")
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(v1, amit).verdict)
        val result = evaluator.evaluate(v2, amit)
        assertEquals(Verdict.ELIGIBLE, result.verdict)
        assertEquals("25", result.lines.first { it.rule == RuleKind.AGE }.params["effectiveMax"])
        assertEquals(40_000L, result.feePaise)
    }

    @Test
    fun poojaFemaleGENBihar26OnCutoffV1FailV2EligibleWithUPDomicileAdvisory() {
        val pooja = profile("1999-02-01", gender = Gender.FEMALE, domicile = "BR")
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(v1, pooja).verdict)
        val result = evaluator.evaluate(v2, pooja)
        assertEquals(Verdict.ELIGIBLE, result.verdict)
        assertTrue(result.lines.any { it.rule == RuleKind.OTHER && it.result == LineResult.INFO })
    }

    @Test
    fun genderSpecificLimitsFemaleWindowIsWiderThanMale() {
        val dob = "1999-02-01" // 26 on cutoff
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(v2, profile(dob, gender = Gender.MALE)).verdict)
        assertEquals(Verdict.ELIGIBLE, evaluator.evaluate(v2, profile(dob, gender = Gender.FEMALE)).verdict)
    }

    @Test
    fun domicileConditionalRelaxationOBCFromUPGets5OBCFromBiharDoesNot() {
        val dob = "1996-01-01" // 29 on cutoff; male limit 22+3=25, +5 → 30
        val upObc = profile(dob, category = Category.OBC, domicile = "UP")
        val brObc = profile(dob, category = Category.OBC, domicile = "BR")
        val up = evaluator.evaluate(v2, upObc)
        assertEquals(Verdict.ELIGIBLE, up.verdict)
        assertEquals("up_reserved", up.lines.first { it.rule == RuleKind.AGE }.params["relaxation"])
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(v2, brObc).verdict)
    }

    @Test
    fun maleBoundariesWithTheGeneralExtension() {
        // v2 male: 22 + 3 = 25 not attained → born on/after 2000-07-02; min 18 → on/before 2007-07-01.
        assertEquals(Verdict.ELIGIBLE, evaluator.evaluate(v2, profile("2000-07-02")).verdict)
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(v2, profile("2000-07-01")).verdict)
        assertEquals(Verdict.ELIGIBLE, evaluator.evaluate(v2, profile("2007-07-01")).verdict)
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(v2, profile("2007-07-02")).verdict)
    }

    @Test
    fun qualification12thAppearingNotAllowed10thOnlyFailsGraduatePasses() {
        val appearing = profile("2003-01-01", quals = listOf(HeldQualification("class12", QualificationStatus.FINAL_YEAR)))
        val appearingResult = evaluator.evaluate(v2, appearing)
        assertEquals(Verdict.NOT_ELIGIBLE, appearingResult.verdict)
        assertEquals(
            ReasonCode.QUAL_APPEARING_NOT_ALLOWED,
            appearingResult.lines.first { it.rule == RuleKind.QUALIFICATION }.code,
        )

        val tenth = profile("2003-01-01", quals = listOf(HeldQualification("class10", QualificationStatus.COMPLETED)))
        assertEquals(Verdict.NOT_ELIGIBLE, evaluator.evaluate(v2, tenth).verdict)

        val grad = profile("2003-01-01", quals = listOf(HeldQualification("grad.bsc", QualificationStatus.COMPLETED)))
        assertEquals(Verdict.ELIGIBLE, evaluator.evaluate(v2, grad).verdict)
    }

    @Test
    fun targetingStatePostReachesUPDomicileOrUPInJobStatesOnly() {
        val up = profile("2003-01-01", domicile = "UP")
        val br = profile("2003-01-01", domicile = "BR")
        assertTrue(evaluator.isTargeted(v2, up))
        assertEquals(false, evaluator.isTargeted(v2, br))
        assertTrue(evaluator.isTargeted(v2, br.copy(jobStates = setOf("UP"))))
    }

    @Test
    fun physicalStandardsAreAdvisoryAndNeverChangeTheVerdict() {
        val result = evaluator.evaluate(v2, profile("2003-01-01"))
        assertEquals(Verdict.ELIGIBLE, result.verdict)
        assertTrue(result.lines.any { it.rule == RuleKind.PHYSICAL && it.result == LineResult.INFO })
    }
}
