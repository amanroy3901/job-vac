package com.sarkarimatch.core.eligibility

import com.sarkarimatch.core.model.AppLanguage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleParsingTest {
    private val evaluator = EligibilityEvaluator()

    @Test
    fun bothExamplesParse() {
        assertEquals("CAPF-CT-GD", Fixtures.sscGd.post.code)
        assertEquals("SSC GD 2026", Fixtures.sscGd.post.shortName?.pick(AppLanguage.HI))
        assertEquals(8, Fixtures.sscGd.age.relaxations.size)
        assertEquals(2, Fixtures.upPolice.age.limits.size)
        assertEquals(3, Fixtures.upPolice.age.generalExtensionYears)
    }

    /** matching-engine.md §3.1: the printed DOB window must reproduce from the formulas (publish validator). */
    @Test
    fun printedDOBWindowReproducesForSSCGD() {
        val age = Fixtures.sscGd.age
        val (after, before) = evaluator.baseDobWindow(age, age.limits.single())
        assertEquals(LocalDate.parse(age.printedDobWindow!!.bornOnOrAfter), after)
        assertEquals(LocalDate.parse(age.printedDobWindow.bornOnOrBefore), before)
        assertEquals(LocalDate.parse("2003-01-02"), after)
        assertEquals(LocalDate.parse("2008-01-01"), before)
    }

    @Test
    fun printedDOBWindowReproducesForUPPoliceMaleRow() {
        val age = Fixtures.upPolice.age
        val male = age.limits.first()
        val (after, before) = evaluator.baseDobWindow(age, male)
        assertEquals(LocalDate.parse("2003-07-02"), after)
        assertEquals(LocalDate.parse("2007-07-01"), before)
    }
}
