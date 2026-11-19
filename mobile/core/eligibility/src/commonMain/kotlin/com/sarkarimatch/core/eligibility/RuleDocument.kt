package com.sarkarimatch.core.eligibility

import com.sarkarimatch.core.model.Category
import com.sarkarimatch.core.model.ExamType
import com.sarkarimatch.core.model.Gender
import com.sarkarimatch.core.model.LocalizedText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Kotlin model of docs/backend/rule.schema.json (v1). Only what the evaluator needs is typed; `review` and
 * provenance (`source`) are ignored (the catalog strips them anyway). Dates are ISO strings in the JSON and parsed
 * lazily as IST calendar dates — never through UTC.
 */

@Serializable
data class RuleDocument(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("evaluator_min_version") val evaluatorMinVersion: Int = 1,
    val post: RulePost,
    @SerialName("exam_tags") val examTags: List<ExamType>,
    val scope: RuleScope? = null,
    val age: AgeRule,
    val qualification: QualificationRule,
    @SerialName("required_attributes") val requiredAttributes: List<RequiredAttribute> = emptyList(),
    val domicile: DomicileRule? = null,
    val gender: GenderRule? = null,
    val category: CategoryRule? = null,
    val pwd: PwdRule? = null,
    @SerialName("ex_serviceman") val exServiceman: ExServicemanRule? = null,
    @SerialName("marital_status") val maritalStatus: MaritalStatusRule? = null,
    val physical: TextRule? = null,
    @SerialName("other_conditions") val otherConditions: List<OtherCondition> = emptyList(),
    val fees: List<FeeRow>,
)

@Serializable
data class RulePost(
    val code: String,
    val name: LocalizedText,
    @SerialName("short_name") val shortName: LocalizedText? = null,
    @SerialName("pay_level") val payLevel: String? = null,
)

@Serializable
data class RuleScope(val kind: String, val state: String? = null)

@Serializable
enum class MaxConvention {
    @SerialName("not_attained") NOT_ATTAINED,
    @SerialName("completed_years") COMPLETED_YEARS,
}

@Serializable
enum class RelaxationMode {
    @SerialName("add_to_max") ADD_TO_MAX,
    @SerialName("after_service_deduction") AFTER_SERVICE_DEDUCTION,
}

@Serializable
enum class ProfileFact {
    @SerialName("esm_service_months") ESM_SERVICE_MONTHS,
    @SerialName("govt_employee") GOVT_EMPLOYEE,
    @SerialName("riot_victim_family") RIOT_VICTIM_FAMILY,
    @SerialName("widow_divorced") WIDOW_DIVORCED,
    @SerialName("other") OTHER,
    ;

    /** Facts the app collects (an empty answer → check_yourself); the others are never collected → row excluded. */
    val collected: Boolean get() = this == ESM_SERVICE_MONTHS || this == GOVT_EMPLOYEE
}

@Serializable
data class AgeRule(
    @SerialName("cutoff_date") val cutoffDate: String,
    @SerialName("max_convention") val maxConvention: MaxConvention = MaxConvention.NOT_ATTAINED,
    val limits: List<AgeLimit>,
    @SerialName("general_extension_years") val generalExtensionYears: Int = 0,
    val relaxations: List<AgeRelaxation> = emptyList(),
    @SerialName("printed_dob_window") val printedDobWindow: DobWindow? = null,
)

@Serializable
data class AgeLimit(
    val `when`: Condition? = null,
    @SerialName("min_years") val minYears: Int,
    @SerialName("max_years") val maxYears: Int,
)

@Serializable
data class AgeRelaxation(
    val id: String,
    val label: LocalizedText? = null,
    val `when`: Condition,
    val years: Int,
    val mode: RelaxationMode,
    @SerialName("absolute_max_years") val absoluteMaxYears: Int? = null,
    @SerialName("needs_profile") val needsProfile: List<ProfileFact> = emptyList(),
)

@Serializable
data class DobWindow(
    val `when`: Condition? = null,
    @SerialName("born_on_or_after") val bornOnOrAfter: String,
    @SerialName("born_on_or_before") val bornOnOrBefore: String,
)

@Serializable
enum class AppearingPolicy {
    @SerialName("not_allowed") NOT_ALLOWED,
    @SerialName("allowed") ALLOWED,
    @SerialName("if_passed_by_date") IF_PASSED_BY_DATE,
}

@Serializable
enum class Equivalence {
    @SerialName("listed_only") LISTED_ONLY,
    @SerialName("check_yourself_same_level") CHECK_YOURSELF_SAME_LEVEL,
}

@Serializable
data class QualificationRule(
    @SerialName("any_of") val anyOf: List<QualificationAlternative>,
    @SerialName("must_complete_by") val mustCompleteBy: String? = null,
    @SerialName("appearing_policy") val appearingPolicy: AppearingPolicy = AppearingPolicy.IF_PASSED_BY_DATE,
    val equivalence: Equivalence = Equivalence.CHECK_YOURSELF_SAME_LEVEL,
    val text: LocalizedText? = null,
)

@Serializable
data class QualificationAlternative(
    val codes: List<String> = emptyList(),
    @SerialName("min_level") val minLevel: Int? = null,
    @SerialName("include_higher") val includeHigher: Boolean = true,
    @SerialName("min_percentage_band") val minPercentageBand: String? = null,
    val `when`: Condition? = null,
)

@Serializable
enum class AttributeStage {
    @SerialName("at_application") AT_APPLICATION,
    @SerialName("before_joining") BEFORE_JOINING,
    @SerialName("preferential") PREFERENTIAL,
}

@Serializable
data class RequiredAttribute(
    @SerialName("any_of_codes") val anyOfCodes: List<String>,
    val stage: AttributeStage,
)

@Serializable
data class DomicileRule(
    @SerialName("required_states") val requiredStates: List<String>? = null,
    val note: LocalizedText? = null,
)

@Serializable
data class GenderRule(val allowed: List<Gender>? = null)

@Serializable
data class CategoryRule(val allowed: List<Category>? = null)

@Serializable
data class PwdRule(
    val eligible: Boolean? = null,
    @SerialName("suitable_types") val suitableTypes: List<String>? = null,
)

@Serializable
data class ExServicemanRule(val eligible: Boolean = true)

@Serializable
data class MaritalStatusRule(val allowed: List<String>? = null)

@Serializable
data class TextRule(val text: LocalizedText? = null)

@Serializable
enum class Severity {
    @SerialName("advisory") ADVISORY,
    @SerialName("check_yourself") CHECK_YOURSELF,
}

@Serializable
data class OtherCondition(
    val text: LocalizedText,
    val severity: Severity,
    val `when`: Condition? = null,
)

@Serializable
data class FeeRow(
    val `when`: Condition? = null,
    @SerialName("amount_paise") val amountPaise: Long,
    val note: LocalizedText? = null,
)

/** `$defs/Condition`: all present fields must match (AND); `any` is an OR over sub-conditions. */
@Serializable
data class Condition(
    val category: List<Category>? = null,
    val gender: List<Gender>? = null,
    @SerialName("domicile_states") val domicileStates: List<String>? = null,
    @SerialName("not_domicile_states") val notDomicileStates: List<String>? = null,
    val pwd: Boolean? = null,
    @SerialName("pwd_types") val pwdTypes: List<String>? = null,
    val esm: Boolean? = null,
    @SerialName("govt_employee") val govtEmployee: String? = null,
    @SerialName("marital_status") val maritalStatus: List<String>? = null,
    @SerialName("has_attribute") val hasAttribute: List<String>? = null,
    val any: List<Condition>? = null,
)

object RuleJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun decode(text: String): RuleDocument = json.decodeFromString(RuleDocument.serializer(), text)
}
