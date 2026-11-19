package com.sarkarimatch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Category {
    @SerialName("gen") GEN,
    @SerialName("ews") EWS,
    @SerialName("obc") OBC,
    @SerialName("sc") SC,
    @SerialName("st") ST,
}

@Serializable
enum class Gender {
    @SerialName("male") MALE,
    @SerialName("female") FEMALE,
    @SerialName("transgender") TRANSGENDER,
}

@Serializable
enum class MaritalStatus {
    @SerialName("unmarried") UNMARRIED,
    @SerialName("married") MARRIED,
    @SerialName("widowed") WIDOWED,
    @SerialName("divorced") DIVORCED,
}

@Serializable
enum class QualificationStatus {
    @SerialName("completed") COMPLETED,
    @SerialName("final_year") FINAL_YEAR,
    @SerialName("appearing") APPEARING,
}

/** Percentage bands as collected in S05 (schema `min_percentage_band`). Ordered low → high. */
@Serializable
enum class PercentageBand {
    @SerialName("lt_50") LT_50,
    @SerialName("50_55") B50_55,
    @SerialName("55_60") B55_60,
    @SerialName("ge_60") GE_60,
}

@Serializable
enum class GovtEmployee {
    @SerialName("central") CENTRAL,
    @SerialName("state") STATE,
    @SerialName("none") NONE,
}

@Serializable
enum class ExamType {
    @SerialName("ssc") SSC,
    @SerialName("railway") RAILWAY,
    @SerialName("banking") BANKING,
    @SerialName("police") POLICE,
    @SerialName("defence") DEFENCE,
    @SerialName("teaching") TEACHING,
    @SerialName("upsc") UPSC,
    @SerialName("state_psc") STATE_PSC,
    @SerialName("other") OTHER,
}
