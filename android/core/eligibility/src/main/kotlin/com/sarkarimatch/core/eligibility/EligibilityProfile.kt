package com.sarkarimatch.core.eligibility

import com.sarkarimatch.core.model.Category
import com.sarkarimatch.core.model.ExamType
import com.sarkarimatch.core.model.Gender
import com.sarkarimatch.core.model.GovtEmployee
import com.sarkarimatch.core.model.MaritalStatus
import com.sarkarimatch.core.model.PercentageBand
import com.sarkarimatch.core.model.QualificationStatus
import java.time.LocalDate

/** One qualification the user holds or is studying for (S05). */
data class HeldQualification(
    val code: String,
    val status: QualificationStatus,
    val resultDate: LocalDate? = null,
    val percentageBand: PercentageBand? = null,
)

/**
 * The evaluation attributes of the local profile (matching-engine.md §2). Lives only on the device.
 * `null` always means "the user did not answer" and never "no".
 */
data class EligibilityProfile(
    val dob: LocalDate,
    val gender: Gender?,
    val category: Category,
    /** `false` → OBC is treated as GEN for relaxations and fees; `null` → OBC with an advisory line. */
    val obcNonCreamyLayer: Boolean? = null,
    /** S07 "पता नहीं": evaluated as GEN; a post that would pass for OBC/SC/ST becomes check_yourself. */
    val categoryUnsure: Boolean = false,
    val domicile: String?,
    val jobStates: Set<String> = emptySet(),
    val pwd: Boolean = false,
    val pwdType: String? = null,
    val esm: Boolean = false,
    val esmServiceMonths: Int? = null,
    val govtEmployee: GovtEmployee? = null,
    val marital: MaritalStatus? = null,
    val qualifications: List<HeldQualification> = emptyList(),
    val extras: Set<String> = emptySet(),
    /** Whether "no extras" really means none (S09). */
    val extrasDeclared: Boolean = false,
    val interests: Set<ExamType> = emptySet(),
) {
    /** `cat_eff`: OBC without non-creamy-layer counts as GEN; "not sure" counts as GEN. */
    val effectiveCategory: Category
        get() = when {
            categoryUnsure -> Category.GEN
            category == Category.OBC && obcNonCreamyLayer == false -> Category.GEN
            else -> category
        }
}
