package com.sarkarimatch.app.e2e

import com.sarkarimatch.app.startDesktopKoin
import com.sarkarimatch.core.data.account.AccountRepository
import com.sarkarimatch.core.data.account.ConfigRepository
import com.sarkarimatch.core.data.catalog.CatalogMatcher
import com.sarkarimatch.core.data.catalog.CatalogSync
import com.sarkarimatch.core.data.catalog.LocalIds
import com.sarkarimatch.core.data.catalog.PostEvaluation
import com.sarkarimatch.core.data.catalog.SyncOutcome
import com.sarkarimatch.core.data.prefs.UserPreferencesRepository
import com.sarkarimatch.core.data.profile.LocalProfile
import com.sarkarimatch.core.data.profile.LocalQualification
import com.sarkarimatch.core.data.profile.ProfileRepository
import com.sarkarimatch.core.data.repository.MatchActionRepository
import com.sarkarimatch.core.data.repository.MatchRepository
import com.sarkarimatch.core.data.repository.UserAction
import com.sarkarimatch.core.database.CatalogDao
import com.sarkarimatch.core.database.MatchDao
import com.sarkarimatch.core.model.AppLanguage
import com.sarkarimatch.core.model.Category
import com.sarkarimatch.core.model.ExamType
import com.sarkarimatch.core.model.Gender
import com.sarkarimatch.core.model.QualificationStatus
import com.sarkarimatch.core.model.Verdict
import com.sarkarimatch.core.network.NoticeDto
import com.sarkarimatch.core.network.SmJson
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.Koin
import org.koin.core.context.stopKoin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end run of the shared data layer + UI on the JVM against a LOCAL backend seeded with
 * `backend/scripts/seed-dev.sh`. Skipped unless `-Psm.e2e=true` (mobile/README.md "Desktop dev target").
 *
 * Fresh database → dev-auth session → config → catalog head/snapshot/batches into Room → sample profile
 * (23-year-old OBC male graduate from UP) → on-device verdicts → Applied (local) → trial on the server →
 * entitlement re-read from the server. Then renders the real screens with that live data to
 * mobile/docs/screenshots/e2e/ (hi + en).
 */
class EndToEndTest {
    private lateinit var koin: Koin
    private lateinit var dir: File

    @Before
    fun setUp() {
        assumeTrue("set -Psm.e2e=true (needs the local backend)", System.getProperty("sm.e2e") == "true")
        dir = Files.createTempDirectory("sm-e2e").toFile()
        koin = startDesktopKoin(System.getProperty("sm.e2e.baseUrl") ?: "http://localhost:8080/", dir).koin
    }

    @After
    fun tearDown() {
        if (::koin.isInitialized) stopKoin()
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    @Test
    fun coreFlowAgainstLocalBackend() = runBlocking {
        val catalog = koin.get<CatalogDao>()
        val matches = koin.get<MatchDao>()
        val accounts = koin.get<AccountRepository>()

        // 0. Empty device.
        assertTrue(catalog.posts().isEmpty() && matches.all().isEmpty(), "fresh database")
        koin.get<UserPreferencesRepository>().setAppLanguage(AppLanguage.HI)

        // 1. Session bootstrap (dev auth: Bearer dev:<uid>) → anonymous account + entitlement.
        val account = accounts.ensureAccount(privacyNoticeVersion = "2026-11-19").getOrThrow()
        log("session: user ${account.userId} (${account.authProvider})")
        assertEquals("anonymous", account.authProvider)
        val free = accounts.entitlement.first()
        assertEquals("free", free.plan)
        assertTrue(free.trialAvailable, "new account can start the trial")

        // 2. Config.
        val config = koin.get<ConfigRepository>().refresh().getOrThrow()
        log("config: update=${config.update} privacy=${config.privacyNoticeVersion} features=${config.features}")
        assertNotNull(config.disclaimer)

        // 3. Catalog sync (no profile yet: nothing matched).
        val sync = assertIs<SyncOutcome.Ok>(koin.get<CatalogSync>().sync())
        log("catalog: seq ${sync.seq}, ${sync.applied} file(s) applied, ${catalog.posts().size} posts, ${catalog.notices().size} notices")
        assertTrue(catalog.posts().size >= EXPECTED.size)
        assertTrue(matches.all().isEmpty())

        // 4. Profile (S04–S09) → on-device matching.
        koin.get<ProfileRepository>().save(SAMPLE_PROFILE)
        val evaluations = koin.get<CatalogMatcher>().rematchAll()
        val byAdvt = evaluations.associateBy { advtOf(catalog, it) }
        evaluations.forEach { e ->
            log("verdict: %-34s %-15s targeted=%s open=%s fee=%s".format(
                advtOf(catalog, e),
                e.verdict,
                e.targeted,
                e.open,
                e.evaluation.feePaise,
            ))
        }
        EXPECTED.forEach { (advt, verdict) ->
            val e = assertNotNull(byAdvt[advt], "seeded notice $advt is in the catalog (run backend/scripts/seed-dev.sh)")
            assertEquals(verdict, e.verdict, "verdict for $advt: ${e.evaluation.lines}")
            assertTrue(e.open, "$advt is still open (the dev seed has fixed Nov–Dec 2026 dates; refresh them when they pass)")
        }

        // 5. Matches feed: eligible + check_yourself only.
        val feed = koin.get<MatchRepository>().observeFeed().first()
        val feedIds = feed.map { it.matchId }.toSet()
        EXPECTED.forEach { (advt, verdict) ->
            val id = LocalIds.matchId(byAdvt.getValue(advt).postId)
            assertEquals(verdict != Verdict.NOT_ELIGIBLE, id in feedIds, "$advt in feed")
        }
        log("feed: ${feed.size} cards: " + feed.joinToString { it.title.pick(AppLanguage.EN) + " [" + it.verdict + "]" })

        // 6. Applied (local Room write) → My forms; leaves the feed.
        val upPolice = LocalIds.matchId(byAdvt.getValue(UP_POLICE).postId)
        val detailBefore = koin.get<MatchRepository>().observeDetail(upPolice).first()
        koin.get<MatchActionRepository>().record(upPolice, UserAction.Applied())
        assertEquals("applied", matches.get(upPolice)?.status)
        val applied = koin.get<MatchRepository>().observeApplied().first()
        assertEquals(listOf(upPolice), applied.map { it.matchId })
        assertFalse(koin.get<MatchRepository>().observeFeed().first().any { it.matchId == upPolice })

        // 7. Trial on the server, then the entitlement re-read from the server.
        val trial = accounts.startTrial().getOrThrow()
        assertEquals("pro", trial.plan)
        assertEquals("trial", trial.source)
        val server = accounts.refreshEntitlement().getOrThrow()
        log(
            "entitlement after trial (GET /v1/me/entitlement): plan=${server.plan} status=${server.status} " +
                "source=${server.source} expires=${server.expiresAt}",
        )
        assertEquals("pro", server.plan)
        assertEquals("active", server.status)
        assertFalse(server.trialAvailable)

        // 8. Screens with this live data, Hindi and English.
        Screenshots(koin, File(System.getProperty("sm.screenshots.dir"))).renderAll(
            feedBeforeApplied = feed,
            upPoliceMatchId = upPolice,
            upPoliceDetailBeforeApplied = assertNotNull(detailBefore),
            ineligibleMatchId = LocalIds.matchId(byAdvt.getValue(NDA).postId),
            checkMatchId = LocalIds.matchId(byAdvt.getValue(UPSSSC).postId),
            freeEntitlement = free,
        )
    }

    private suspend fun advtOf(catalog: CatalogDao, e: PostEvaluation): String =
        catalog.notice(e.noticeId)?.let { SmJson.decodeFromString(NoticeDto.serializer(), it.bodyJson).advtNo }.orEmpty()

    private fun log(msg: String) = println("[e2e] $msg")

    companion object {
        const val UP_POLICE = "DEV-SEED/PRPB-1-1(CP)/2026"
        const val NDA = "DEV-SEED/NDA-NA-I/2027"
        const val UPSSSC = "DEV-SEED/07-Exam/2026"

        /** backend/scripts/seed/<slug>.notice.json → expected verdict for [SAMPLE_PROFILE]. */
        val EXPECTED = linkedMapOf(
            "DEV-SEED/HQ-C-3301/2/2026" to Verdict.ELIGIBLE, // SSC GD 2027: 23 > 23-limit, OBC +3 → 26
            UP_POLICE to Verdict.ELIGIBLE, //                     UP Police: male 22, UP OBC +5 → 27
            "DEV-SEED/CEN-05/2026" to Verdict.ELIGIBLE, //      RRB NTPC graduate: 18–33
            "DEV-SEED/CRP-CSA-XVI" to Verdict.ELIGIBLE, //      IBPS Clerk: 20–28, graduate
            UPSSSC to Verdict.CHECK_YOURSELF, //                UPSSSC JA: CCC + Hindi typing not declared, PET scorecard
            NDA to Verdict.NOT_ELIGIBLE, //                     NDA: 16–19, too old
        )

        /** 23-year-old OBC (non-creamy) male graduate (BA) from UP, interested in every exam type. */
        val SAMPLE_PROFILE = LocalProfile(
            dob = LocalDate(2003, 5, 15),
            gender = Gender.MALE,
            category = Category.OBC,
            obcNonCreamyLayer = true,
            domicile = "UP",
            qualifications = listOf(LocalQualification("grad.ba", QualificationStatus.COMPLETED)),
            interests = ExamType.entries.toSet(),
        )
    }
}
