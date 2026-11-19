package com.sarkarimatch.core.data.repository

import com.sarkarimatch.core.data.catalog.LocalIds
import com.sarkarimatch.core.database.ApplicationEntity
import com.sarkarimatch.core.database.MatchActionEntity
import com.sarkarimatch.core.database.MatchDao
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** User actions on a match (data-layer.md §5). Local Room writes only: they never reach the server. */
sealed interface UserAction {
    val type: String

    data object Seen : UserAction { override val type = "seen" }
    data class Applied(val feePaid: Boolean? = null) : UserAction { override val type = "applied" }
    data class NotInterested(val reasons: Set<String> = emptySet()) : UserAction { override val type = "not_interested" }
}

data class RecordedAction(val actionId: String, val matchId: String, val previousStatus: String)

class MatchActionRepository(private val dao: MatchDao, private val clock: Clock = Clock.System) {
    /**
     * One Room transaction: status change + action log (+ the local application for Applied).
     * TODO(FE-14/FE-08): cancel reminders, topic (un)subscribe, B02 queueing.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun record(matchId: String, action: UserAction): RecordedAction {
        val match = requireNotNull(dao.get(matchId)) { "no match $matchId" }
        val now = clock.now().toEpochMilliseconds()
        val status = when (action) {
            UserAction.Seen -> if (match.status == "new") "seen" else match.status
            is UserAction.Applied -> "applied"
            is UserAction.NotInterested -> "not_interested"
        }
        val actionId = "act_" + Uuid.random().toHexString()
        val payload = when (action) {
            is UserAction.NotInterested -> action.reasons.sorted().joinToString(",")
            is UserAction.Applied -> action.feePaid?.toString()
            UserAction.Seen -> null
        }
        val application = (action as? UserAction.Applied)?.let {
            ApplicationEntity(
                id = LocalIds.applicationId(match.postId),
                matchId = match.id,
                noticeId = match.noticeId,
                postId = match.postId,
                appliedAtEpochMs = now,
                feePaid = it.feePaid,
                updatedAtEpochMs = now,
            )
        }
        dao.record(
            updated = match.copy(status = status, updatedAtEpochMs = now),
            action = MatchActionEntity(actionId, matchId, action.type, now, match.status, payload),
            application = application,
            removeApplication = false,
        )
        return RecordedAction(actionId, matchId, match.status)
    }

    /** Undo (6 s snackbar): restores the previous status and drops the application created by Applied. */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun undo(recorded: RecordedAction) {
        val match = dao.get(recorded.matchId) ?: return
        val now = clock.now().toEpochMilliseconds()
        dao.record(
            updated = match.copy(status = recorded.previousStatus, updatedAtEpochMs = now),
            action = MatchActionEntity("act_" + Uuid.random().toHexString(), match.id, "undo", now, match.status, recorded.actionId),
            application = null,
            removeApplication = match.status == "applied",
        )
    }
}
