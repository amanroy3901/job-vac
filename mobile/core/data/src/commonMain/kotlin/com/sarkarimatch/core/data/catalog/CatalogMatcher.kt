package com.sarkarimatch.core.data.catalog

import com.sarkarimatch.core.data.profile.ProfileRepository
import com.sarkarimatch.core.database.CatalogDao
import com.sarkarimatch.core.database.CatalogNoticeEntity
import com.sarkarimatch.core.database.CatalogPostEntity
import com.sarkarimatch.core.database.MatchDao
import com.sarkarimatch.core.database.MatchEntity
import com.sarkarimatch.core.eligibility.Evaluation
import com.sarkarimatch.core.model.Ist
import com.sarkarimatch.core.model.Verdict
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/** The verdict of one catalog post for the local profile (whether or not it becomes a match). */
data class PostEvaluation(
    val postId: String,
    val noticeId: String,
    val targeted: Boolean,
    val open: Boolean,
    val evaluation: Evaluation,
) {
    val verdict: Verdict get() = evaluation.verdict
}

/** Derived ids (docs/api/README.md §1a). */
object LocalIds {
    fun matchId(postId: String) = "m_" + postId.removePrefix("p_")
    fun applicationId(postId: String) = "a_" + postId.removePrefix("p_")
}

/**
 * Runs the on-device evaluator over the local catalog and keeps `matches` in step (matching-engine.md §5, data-layer
 * §6): eligible / check_yourself posts that are targeted and still open become matches; user statuses (applied, not
 * interested, snoozed, seen) are kept; open matches that no longer qualify become `withdrawn`; past last dates
 * become `expired`.
 */
class CatalogMatcher(
    private val catalog: CatalogDao,
    private val matches: MatchDao,
    private val profiles: ProfileRepository,
    private val engine: MatchEngine,
    private val clock: Clock = Clock.System,
) {
    /** Evaluates every catalog post against the current profile (empty without a profile). */
    suspend fun evaluateAll(): List<PostEvaluation> {
        val profile = profiles.get()?.toEligibility() ?: return emptyList()
        val notices = catalog.notices().associateBy { it.id }
        val today = Ist.today(clock)
        return catalog.posts().mapNotNull { post ->
            val notice = notices[post.noticeId] ?: return@mapNotNull null
            val rule = runCatching { engine.decode(post.ruleJson) }.getOrNull() ?: return@mapNotNull null
            PostEvaluation(
                postId = post.id,
                noticeId = notice.id,
                targeted = engine.isTargeted(rule, profile),
                open = isOpen(notice, post, today),
                evaluation = engine.evaluate(rule, profile),
            )
        }
    }

    /** Re-evaluates everything and writes the match diff. Returns the evaluations for callers/tests. */
    suspend fun rematchAll(): List<PostEvaluation> {
        val evaluations = evaluateAll()
        val notices = catalog.notices().associateBy { it.id }
        val now = clock.now().toEpochMilliseconds()
        val existing = matches.all().associateBy { it.id }
        val writes = mutableListOf<MatchEntity>()
        val seen = mutableSetOf<String>()
        for (e in evaluations) {
            val id = LocalIds.matchId(e.postId)
            seen += id
            val old = existing[id]
            val notice = notices.getValue(e.noticeId)
            val qualifies = e.targeted && e.open && e.verdict != Verdict.NOT_ELIGIBLE && !notice.newMatchPaused
            val lastDate = notice.applyEnd ?: continue
            when {
                qualifies -> writes += MatchEntity(
                    id = id,
                    postId = e.postId,
                    noticeId = e.noticeId,
                    verdict = e.verdict.wire(),
                    status = old?.status?.takeUnless { it in RESETTABLE } ?: "new",
                    ruleVersion = catalog.post(e.postId)?.ruleVersion ?: 1,
                    reasonCodes = e.evaluation.reasonCodes.joinToString(","),
                    feePaise = e.evaluation.feePaise,
                    lastDate = lastDate,
                    lastDateTime = notice.applyEndTime,
                    matchedAtEpochMs = old?.matchedAtEpochMs ?: now,
                    updatedAtEpochMs = now,
                )
                old != null && old.status !in FINAL -> writes += old.copy(
                    status = if (!e.open) "expired" else "withdrawn",
                    updatedAtEpochMs = now,
                )
            }
        }
        // Posts removed from the catalog: their open matches are withdrawn.
        existing.values.filter { it.id !in seen && it.status !in FINAL }.forEach {
            writes += it.copy(status = "withdrawn", updatedAtEpochMs = now)
        }
        if (writes.isNotEmpty()) matches.upsert(writes)
        return evaluations
    }

    private fun isOpen(notice: CatalogNoticeEntity, post: CatalogPostEntity, today: LocalDate): Boolean {
        if (notice.status !in setOf("open", "upcoming") || post.status != "active") return false
        val end = notice.applyEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        return end >= today
    }

    private companion object {
        /** Statuses a re-evaluation never overrides. */
        val FINAL = setOf("applied", "not_interested", "expired", "withdrawn")

        /** Statuses that restart as `new` when the post qualifies again. */
        val RESETTABLE = setOf("withdrawn", "expired")
    }
}

fun Verdict.wire(): String = when (this) {
    Verdict.ELIGIBLE -> "eligible"
    Verdict.CHECK_YOURSELF -> "check_yourself"
    Verdict.NOT_ELIGIBLE -> "not_eligible"
}

fun verdictOf(wire: String): Verdict = when (wire) {
    "eligible" -> Verdict.ELIGIBLE
    "check_yourself" -> Verdict.CHECK_YOURSELF
    else -> Verdict.NOT_ELIGIBLE
}
