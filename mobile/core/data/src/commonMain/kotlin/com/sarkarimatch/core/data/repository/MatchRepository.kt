package com.sarkarimatch.core.data.repository

import com.sarkarimatch.core.data.catalog.LocalIds
import com.sarkarimatch.core.data.catalog.MatchEngine
import com.sarkarimatch.core.data.catalog.verdictOf
import com.sarkarimatch.core.data.profile.ProfileRepository
import com.sarkarimatch.core.database.CatalogDao
import com.sarkarimatch.core.database.MatchDao
import com.sarkarimatch.core.eligibility.AttributeStage
import com.sarkarimatch.core.eligibility.LineResult
import com.sarkarimatch.core.eligibility.ReasonCode
import com.sarkarimatch.core.eligibility.RuleDocument
import com.sarkarimatch.core.model.ExamCategoryTile
import com.sarkarimatch.core.model.Ist
import com.sarkarimatch.core.model.LocalizedText
import com.sarkarimatch.core.model.MatchCardUi
import com.sarkarimatch.core.model.Verdict
import com.sarkarimatch.core.network.NoticeDto
import com.sarkarimatch.core.network.PostDto
import com.sarkarimatch.core.network.SmJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** One "why you match / what you need" line for S21. */
data class WhyLineUi(val result: LineResult, val text: LocalizedText)

/** Everything S21 (job detail) shows; evaluated live against the local profile. */
data class JobDetailUi(
    val matchId: String,
    val postId: String,
    val noticeId: String,
    val title: LocalizedText,
    val organisation: LocalizedText,
    val postName: LocalizedText,
    val vacancies: Int?,
    val tile: ExamCategoryTile,
    val verdict: Verdict,
    /** Match status (`new`, `applied`, …); null when the post is not one of the user's matches. */
    val status: String?,
    val feePaise: Long?,
    val feeNote: LocalizedText?,
    val qualification: LocalizedText,
    val lastDate: LocalDate,
    val lastTime: String?,
    val applyStart: LocalDate?,
    val why: List<WhyLineUi>,
    val officialPdfUrl: String?,
    val applyUrl: String?,
)

/** One row of S22 "My forms". */
data class AppliedFormUi(
    val applicationId: String,
    val matchId: String,
    val title: LocalizedText,
    val organisation: LocalizedText,
    val tile: ExamCategoryTile,
    val appliedOn: LocalDate,
    val lastDate: LocalDate?,
)

/** Matches and their catalog content for the job screens (data-layer.md §8). */
interface MatchRepository {
    /** S20: open matches (not applied / hidden / expired). */
    fun observeFeed(): Flow<List<MatchCardUi>>

    /** S21 for [matchId] (works for posts that are not a match too, e.g. shared links). */
    fun observeDetail(matchId: String): Flow<JobDetailUi?>

    /** S22. */
    fun observeApplied(): Flow<List<AppliedFormUi>>
}

/** Room + on-device evaluation. */
class RoomMatchRepository(
    private val matches: MatchDao,
    private val catalog: CatalogDao,
    private val profiles: ProfileRepository,
    private val engine: MatchEngine,
) : MatchRepository {
    override fun observeFeed(): Flow<List<MatchCardUi>> =
        combine(matches.observeOpen(), profiles.profile, catalog.observePostCount()) { rows, profile, _ -> rows to profile }
            .map { (rows, profile) ->
                val elig = profile?.toEligibility()
                rows.mapNotNull { m ->
                    val post = catalog.post(m.postId) ?: return@mapNotNull null
                    val notice = catalog.notice(m.noticeId)?.let { decodeNotice(it.bodyJson) } ?: return@mapNotNull null
                    val postDto = decodePost(post.postJson)
                    val rule = runCatching { engine.decode(post.ruleJson) }.getOrNull() ?: return@mapNotNull null
                    val eval = elig?.let { engine.evaluate(rule, it) }
                    val qualText = shortQualification(rule, attributes = false)
                    MatchCardUi(
                        matchId = m.id,
                        title = notice.shortTitle ?: notice.title,
                        organisation = notice.org,
                        vacancies = postDto.vacanciesTotal,
                        tile = tileOf(notice.examType),
                        verdict = verdictOf(m.verdict),
                        feePaise = m.feePaise,
                        qualification = shortQualification(rule),
                        lastDate = LocalDate.parse(m.lastDate),
                        lastTime = m.lastDateTime,
                        whyLine = eval?.let { Explanations.headline(it.lines) }?.let { Explanations.render(it, qualText) },
                    )
                }
            }

    override fun observeDetail(matchId: String): Flow<JobDetailUi?> =
        combine(matches.observe(matchId), profiles.profile, catalog.observePostCount()) { m, profile, _ -> m to profile }
            .map { (m, profile) ->
                val postId = m?.postId ?: ("p_" + matchId.removePrefix("m_"))
                val post = catalog.post(postId) ?: return@map null
                val notice = catalog.notice(post.noticeId)?.let { decodeNotice(it.bodyJson) } ?: return@map null
                val postDto = decodePost(post.postJson)
                val rule = engine.decode(post.ruleJson)
                val eval = profile?.let { engine.evaluate(rule, it.toEligibility()) }
                val qualText = shortQualification(rule, attributes = false)
                val why = eval?.lines.orEmpty()
                    .filter { it.code != ReasonCode.FEE && it.code != ReasonCode.PWD_OK }
                    .mapNotNull { line -> Explanations.render(line, qualText)?.let { WhyLineUi(line.result, it) } }
                val fee = eval?.feePaise
                val end = notice.applyEnd
                JobDetailUi(
                    matchId = LocalIds.matchId(postId),
                    postId = postId,
                    noticeId = notice.id,
                    title = notice.shortTitle ?: notice.title,
                    organisation = notice.org,
                    postName = postDto.name,
                    vacancies = postDto.vacanciesTotal,
                    tile = tileOf(notice.examType),
                    verdict = eval?.verdict ?: m?.verdict?.let(::verdictOf) ?: Verdict.CHECK_YOURSELF,
                    status = m?.status,
                    feePaise = fee,
                    feeNote = rule.fees.firstOrNull { it.amountPaise == fee }?.note,
                    qualification = rule.qualification.text ?: shortQualification(rule),
                    lastDate = end?.let { LocalDate.parse(it.date) } ?: Ist.today(),
                    lastTime = end?.time,
                    applyStart = notice.keyDates.firstOrNull { it.kind == "apply_start" }?.let { LocalDate.parse(it.date) },
                    why = why,
                    officialPdfUrl = notice.links.firstOrNull { it.kind == "official_pdf" }?.url,
                    applyUrl = notice.links.firstOrNull { it.kind == "apply" }?.url,
                )
            }

    override fun observeApplied(): Flow<List<AppliedFormUi>> = matches.observeApplications().map { apps ->
        apps.mapNotNull { a ->
            val notice = catalog.notice(a.noticeId)?.let { decodeNotice(it.bodyJson) } ?: return@mapNotNull null
            AppliedFormUi(
                applicationId = a.id,
                matchId = a.matchId,
                title = notice.shortTitle ?: notice.title,
                organisation = notice.org,
                tile = tileOf(notice.examType),
                appliedOn = Instant.fromEpochMilliseconds(a.appliedAtEpochMs).toLocalDateTime(TimeZone.of("Asia/Kolkata")).date,
                lastDate = notice.applyEnd?.let { LocalDate.parse(it.date) },
            )
        }
    }

    private fun decodeNotice(json: String) = SmJson.decodeFromString(NoticeDto.serializer(), json)
    private fun decodePost(json: String) = SmJson.decodeFromString(PostDto.serializer(), json)

    companion object {
        fun tileOf(examType: String?): ExamCategoryTile = when (examType) {
            "police" -> ExamCategoryTile.Police
            "railway" -> ExamCategoryTile.Railway
            "banking" -> ExamCategoryTile.Bank
            "ssc", "upsc", "defence" -> ExamCategoryTile.Ssc
            else -> ExamCategoryTile.State
        }

        private val QUAL_NAMES = mapOf(
            "class10" to ("10वीं पास" to "10th pass"),
            "class12" to ("12वीं पास" to "12th pass"),
            "iti" to ("ITI" to "ITI"),
            "diploma" to ("डिप्लोमा" to "Diploma"),
            "grad" to ("ग्रेजुएट" to "Graduate"),
            "pg" to ("पोस्ट ग्रेजुएट" to "Post-graduate"),
        )

        /** "12वीं पास", "ग्रेजुएट + CCC": the card's qualification chip. */
        fun shortQualification(rule: RuleDocument, attributes: Boolean = true): LocalizedText {
            val code = rule.qualification.anyOf.firstOrNull()?.codes?.firstOrNull().orEmpty()
            val (hi, en) = QUAL_NAMES[code.substringBefore('.')] ?: (code to code)
            val atApplication = rule.requiredAttributes.filter { it.stage == AttributeStage.AT_APPLICATION }
            val extra = if (!attributes) "" else atApplication.flatMap { it.anyOfCodes }
                .firstOrNull()?.uppercase()?.let { " + $it" }.orEmpty()
            return if (extra.isEmpty()) LocalizedText.of("hi" to hi, "en" to en)
            else LocalizedText.of("hi" to hi.removeSuffix(" पास") + extra, "en" to en.removeSuffix(" pass") + extra)
        }
    }
}
