package com.sarkarimatch.core.data.catalog

import com.sarkarimatch.core.database.CatalogDao
import com.sarkarimatch.core.database.CatalogNoticeEntity
import com.sarkarimatch.core.database.CatalogPostEntity
import com.sarkarimatch.core.database.CatalogStateEntity
import com.sarkarimatch.core.model.AppLanguage
import com.sarkarimatch.core.network.ApiResult
import com.sarkarimatch.core.network.CatalogFileDto
import com.sarkarimatch.core.network.SmApi
import com.sarkarimatch.core.network.SmJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface SyncOutcome {
    /** [seq] is the local catalog position after the sync; [applied] counts snapshot + batches applied. */
    data class Ok(val seq: Long, val applied: Int, val evaluations: List<PostEvaluation>) : SyncOutcome
    data class Failed(val reason: String) : SyncOutcome
}

/**
 * Catalog sync (docs/api/README.md §6, data-layer.md §6): head → snapshot (empty or stale local catalog) → every
 * newer batch in order, each applied in one Room transaction, then on-device matching. Concurrent triggers coalesce
 * on a mutex.
 *
 * TODO(FE-13): verify each file's sha256 against the head (needs a common SHA-256; the response ETag is the same hash).
 */
class CatalogSync(
    private val api: SmApi,
    private val dao: CatalogDao,
    private val matcher: CatalogMatcher,
    private val language: () -> AppLanguage,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncOutcome = mutex.withLock {
        val locales = localesFor(language())
        val head = when (val r = api.getCatalogHead()) {
            is ApiResult.Ok -> r.value
            else -> return SyncOutcome.Failed("head: $r")
        }
        val now = clock.now().toEpochMilliseconds()
        val offset = runCatching { Instant.parse(head.serverTime).toEpochMilliseconds() - now }.getOrDefault(0)
        val state = dao.state(locales)
        var seq = state?.seq ?: 0L
        var snapshotSeq = state?.snapshotSeq ?: 0L
        var applied = 0
        val snapshot = head.snapshot
        if (snapshot != null && (seq == 0L || seq < snapshot.seq)) {
            val file = when (val r = api.getCatalogSnapshot(snapshot.seq, locales)) {
                is ApiResult.Ok -> r.value
                else -> return SyncOutcome.Failed("snapshot ${snapshot.seq}: $r")
            }
            seq = snapshot.seq
            snapshotSeq = snapshot.seq
            apply(file, replaceAll = true, CatalogStateEntity(locales, seq, snapshotSeq, clock.now().toEpochMilliseconds(), offset))
            applied++
        }
        for (b in head.batches.filter { it.seq > seq }.sortedBy { it.seq }) {
            val file = when (val r = api.getCatalogBatch(b.seq, locales)) {
                is ApiResult.Ok -> r.value
                else -> break // stay on the previous seq; retried on the next trigger
            }
            seq = b.seq
            apply(file, replaceAll = false, CatalogStateEntity(locales, seq, snapshotSeq, clock.now().toEpochMilliseconds(), offset))
            applied++
        }
        SyncOutcome.Ok(seq, applied, matcher.rematchAll())
    }

    private suspend fun apply(file: CatalogFileDto, replaceAll: Boolean, state: CatalogStateEntity) {
        val notices = file.notices.map { n ->
            val end = n.applyEnd
            CatalogNoticeEntity(
                id = n.id,
                version = n.version,
                catalogSeq = n.catalogSeq,
                status = n.status,
                applyEnd = end?.date,
                applyEndTime = end?.time,
                newMatchPaused = n.newMatchPaused,
                bodyJson = SmJson.encodeToString(com.sarkarimatch.core.network.NoticeDto.serializer(), n.copy(posts = emptyList())),
            )
        }
        val posts = file.notices.flatMap { n ->
            n.posts.mapNotNull { p ->
                val rule = p.rule ?: return@mapNotNull null
                CatalogPostEntity(
                    id = p.id,
                    noticeId = n.id,
                    ruleVersion = p.ruleVersion,
                    evaluatorMinVersion = rule["evaluator_min_version"]?.toString()?.toIntOrNull() ?: 1,
                    status = p.status,
                    ruleJson = rule.toString(),
                    postJson = SmJson.encodeToString(com.sarkarimatch.core.network.PostDto.serializer(), p.copy(rule = null)),
                )
            }
        }
        dao.apply(notices, posts, file.removedNoticeIds, replaceAll, state)
    }

    companion object {
        /** Launch locales are always fetched so a language switch needs no new download (`en,hi`). */
        fun localesFor(language: AppLanguage): String =
            (setOf("en", "hi", language.tag) + language.fallback).sorted().joinToString(",")
    }
}
