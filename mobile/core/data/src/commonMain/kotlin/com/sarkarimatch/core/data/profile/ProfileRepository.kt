package com.sarkarimatch.core.data.profile

import com.sarkarimatch.core.database.ProfileDao
import com.sarkarimatch.core.database.ProfileEntity
import com.sarkarimatch.core.eligibility.EligibilityProfile
import com.sarkarimatch.core.eligibility.HeldQualification
import com.sarkarimatch.core.model.Category
import com.sarkarimatch.core.model.ExamType
import com.sarkarimatch.core.model.Gender
import com.sarkarimatch.core.model.MaritalStatus
import com.sarkarimatch.core.model.PercentageBand
import com.sarkarimatch.core.model.QualificationStatus
import com.sarkarimatch.core.network.SmJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/** One qualification as stored in the local profile (S05). */
@Serializable
data class LocalQualification(
    val code: String,
    val status: QualificationStatus = QualificationStatus.COMPLETED,
    @SerialName("result_date") val resultDate: LocalDate? = null,
    @SerialName("percentage_band") val percentageBand: PercentageBand? = null,
)

/**
 * The on-device profile (openapi `LocalProfile`, data-layer.md §3). It never leaves the phone except inside the
 * end-to-end-encrypted backup. `null` fields mean "not answered".
 */
@Serializable
data class LocalProfile(
    val dob: LocalDate,
    val gender: Gender? = null,
    val category: Category = Category.GEN,
    @SerialName("obc_ncl") val obcNonCreamyLayer: Boolean? = null,
    @SerialName("category_unsure") val categoryUnsure: Boolean = false,
    val domicile: String? = null,
    @SerialName("job_states") val jobStates: Set<String> = emptySet(),
    val pwd: Boolean = false,
    val esm: Boolean = false,
    val marital: MaritalStatus? = null,
    val qualifications: List<LocalQualification> = emptyList(),
    val extras: Set<String> = emptySet(),
    @SerialName("extras_declared") val extrasDeclared: Boolean = false,
    val interests: Set<ExamType> = emptySet(),
) {
    fun toEligibility() = EligibilityProfile(
        dob = dob,
        gender = gender,
        category = category,
        obcNonCreamyLayer = obcNonCreamyLayer,
        categoryUnsure = categoryUnsure,
        domicile = domicile,
        jobStates = jobStates,
        pwd = pwd,
        esm = esm,
        marital = marital,
        qualifications = qualifications.map { HeldQualification(it.code, it.status, it.resultDate, it.percentageBand) },
        extras = extras,
        extrasDeclared = extrasDeclared,
        interests = interests,
    )
}

/** Room single row; saving re-runs matching (wired in [com.sarkarimatch.core.data.catalog.CatalogMatcher]). */
class ProfileRepository(private val dao: ProfileDao, private val clock: Clock = Clock.System) {
    val profile: Flow<LocalProfile?> = dao.observe().map { it?.let(::decode) }

    suspend fun get(): LocalProfile? = dao.get()?.let(::decode)

    suspend fun save(profile: LocalProfile) {
        val version = (dao.get()?.version ?: 0) + 1
        dao.upsert(ProfileEntity(
            json = SmJson.encodeToString(LocalProfile.serializer(), profile),
            version = version,
            updatedAtEpochMs = clock.now().toEpochMilliseconds(),
        ))
    }

    private fun decode(e: ProfileEntity) = SmJson.decodeFromString(LocalProfile.serializer(), e.json)
}

/**
 * Onboarding answers collected step by step (S04–S09) before the profile is saved on S10. Process-scoped, never
 * persisted (data-layer.md keeps the real draft in Room with FE-10).
 */
class ProfileDraft {
    private val _state = MutableStateFlow<LocalProfile?>(null)
    val state: StateFlow<LocalProfile?> = _state

    fun update(transform: (LocalProfile?) -> LocalProfile?) {
        _state.value = transform(_state.value)
    }
}
