package com.sarkarimatch.core.eligibility

import com.sarkarimatch.core.model.Category
import com.sarkarimatch.core.model.Gender
import com.sarkarimatch.core.model.QualificationStatus
import java.time.LocalDate

/** The two worked examples from docs/backend/examples, copied as test resources. */
object Fixtures {
    fun rule(name: String): RuleDocument {
        val text = checkNotNull(javaClass.getResource("/rules/$name.rule.json")) { "missing fixture $name" }.readText()
        return RuleJson.decode(text)
    }

    val sscGd: RuleDocument by lazy { rule("ssc-gd-2026") }
    val upPolice: RuleDocument by lazy { rule("up-police-constable-2025") }

    fun profile(
        dob: String,
        gender: Gender? = Gender.MALE,
        category: Category = Category.GEN,
        domicile: String? = "UP",
        quals: List<HeldQualification> = listOf(HeldQualification("class12", QualificationStatus.COMPLETED)),
        obcNcl: Boolean? = true,
        esm: Boolean = false,
        esmServiceMonths: Int? = null,
        pwd: Boolean = false,
        categoryUnsure: Boolean = false,
    ) = EligibilityProfile(
        dob = LocalDate.parse(dob),
        gender = gender,
        category = category,
        obcNonCreamyLayer = if (category == Category.OBC) obcNcl else null,
        categoryUnsure = categoryUnsure,
        domicile = domicile,
        pwd = pwd,
        esm = esm,
        esmServiceMonths = esmServiceMonths,
        qualifications = quals,
    )
}
