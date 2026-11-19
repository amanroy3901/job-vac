# Data layer

Contract: `docs/api/openapi.yaml` (v1) + `docs/api/README.md`. Every repository call that touches the network maps to
an `operationId` (§8).

**Founder decisions of 2026-11-19 (ADR-11 in `docs/backend/README.md`):** the eligibility profile, matching,
matches, reminders, actions, applications, inbox and notification preferences live **only on the device**. The app
reads a public, CDN-cached **catalog** and evaluates it locally with `:matching-core` (the same KMP module the server
uses for review previews). The server holds only what an account feature needs (Google sign-in, Pro/trial, family
link, encrypted backup). There is no phone number, OTP, WhatsApp or UPI anywhere.

**Platforms (founder decision 9):** everything in this file is `commonMain` Kotlin shared by Android and iOS unless a
row says otherwise. Platform pieces are injected through Koin ports (platform-interop.md §2): SQLite driver and file
paths, the Ktor engine, the background scheduler (WorkManager / `BGTaskScheduler`), the backup key store (Block Store /
iCloud Keychain) and the auth SDKs.

## 1. Shape

```mermaid
flowchart LR
  subgraph UI["feature.* (CMP + KMP ViewModel)"]
    VM[ViewModel]
  end
  subgraph DATA[":core:data"]
    R[Repositories<br/>(interfaces + impl)]
    CS[CatalogSync]
    ME[MatchEngine<br/>(:matching-core)]
    RP[ReminderPlanner<br/>(:reminder-core)]
    TS[TopicSync]
    OB[Outbox<br/>(account writes only)]
    DS[(DataStore<br/>app prefs)]
    W[Background jobs (common bodies, BackgroundScheduler port):<br/>catalog-sync, reconcile (daily),<br/>reminders, outbox, backup,<br/>watchlist, telemetry, push-test,<br/>purchase-verify, cleanup<br/>Android: WorkManager · iOS: BGTaskScheduler + foreground]
  end
  subgraph DB["core.database (Room KMP)"]
    T[(catalog_notices, catalog_posts, lifecycle_events,<br/>profile, profile_draft, matches, match_actions,<br/>applications, local_reminders, notif_ledger, inbox,<br/>notif_prefs, ref_items, languages, entitlement, account,<br/>family_links, family_following, consent_records,<br/>usage_counters, outbox, catalog_state)]
  end
  subgraph NET["core.network"]
    API[SmApi (Ktor HttpClient)]
    IC[Ktor plugins: auth (account calls),<br/>App Check, headers, problem+json]
  end
  FB[(Firebase Auth / App Check /<br/>Installations / FCM topics<br/>via AuthPlatform / PushPlatform ports)]
  VM -->|observe Flow| R
  VM -->|commands| R
  R -->|read Flow| T
  R -->|write| T
  CS --> API
  CS -->|apply batch in tx| T
  CS --> ME --> T
  ME --> RP --> T
  ME --> TS --> FB
  R --> OB --> W --> API
  API --> IC --> FB
```

Rules:

1. **Room is the single source of truth** for everything a screen shows, and for most data it is the **only** copy:
   profile, matches, actions, applications + timeline, inbox, prefs. UI only observes Room `Flow`s.
2. **Verdicts are computed on the device** by `MatchEngine` from the local profile and the catalog's published rules,
   never fetched. Re-evaluation happens on every catalog batch, profile save and the daily reconcile.
3. **User actions are plain local writes** (Applied / Not interested / Snooze / Seen / Undo / Confirm eligible). They
   take effect instantly, offline, from notification buttons without opening the app (Android `BroadcastReceiver`, iOS
   background `UNNotificationResponse`), and are never sent to the server. Only account-level writes (backup, family watch-list, consents, device, reports, counters) are queued.
4. Repositories expose **domain models** from `:core:model`, never DTOs or entities.
5. Dispatchers are injected (`named("io")` in Koin; `Dispatchers.IO` exists on Kotlin/Native too) — no literals, so `commonTest` can use `StandardTestDispatcher`.
7. **Time is `kotlinx-datetime`** (`Instant`, `LocalDate`, `TimeZone.of("Asia/Kolkata")`) in common code; `java.time` does not exist on iOS.
6. **Minor mode** (`ProfileRepository.observeIsMinor()`, DOB < 18 in IST): `AccountRepository`, `EntitlementRepository`
   purchase/trial, `BackupRepository`, `FamilyRepository` (owner side), `ReportRepository.attributes`,
   `UsageCounters` and the ads gate are hard-disabled in the repository layer, not just hidden in UI.

## 2. Network (`:core:data`, package `core.network`)

- **Ktor client 3** + `ContentNegotiation` with kotlinx.serialization (`Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }`).
  Engines: **OkHttp** on Android (HTTP/2, the system's proven TLS stack on old devices), **Darwin** (`NSURLSession`) on
  iOS (system TLS, ATS, proxy and cellular-data settings respected). The engine is the only platform part
  (`expect fun httpEngine(): HttpClientEngineFactory<*>`, a stateless `expect`/`actual`).
- DTOs hand-written in `commonMain` `com.sarkarimatch.core.network.dto`, mirroring OpenAPI schemas 1:1 (≈ 30 classes;
  the catalog's `RuleDocument` is parsed by `:matching-core`'s own serializer). A CI job diffs DTO field names against
  `openapi.yaml`.
- `LocalizedText` is a `Map<LanguageCode, String>` with a custom serializer; unknown keys are kept (forward
  compatibility for languages added later).
- Unknown enum values map to the fallbacks the spec documents (push `type` unknown → `sync`).

```kotlin
class SmApi(private val http: HttpClient) {          // commonMain; one method per operationId (§8)
    // public, CDN (no auth, no App Check)
    suspend fun getCatalogHead(etag: String?): Cached<CatalogHeadDto> = http.get("v1/catalog/head") { etag?.let { header(IfNoneMatch, it) } }.cached()
    suspend fun <T> getCatalogSnapshot(seq: Long, locales: String, consume: suspend (Source) -> T): T =   // streamed
        http.prepareGet("v1/catalog/snapshots/$seq") { parameter("locales", locales) }.execute { consume(it.bodyAsChannel().asSource()) }
    suspend fun getAppConfig(etag: String?): Cached<AppConfigDto> = /* … */ TODO()
    // App Check only, unlinkable (no installation id header)
    suspend fun reportMatch(key: IdempotencyKey, body: ReportInputDto): MatchReportDto =
        http.post("v1/reports") { call(Call.LimitedUse); idempotency(key); setBody(body) }.body()
    // account calls (ID token)
    suspend fun startSession(key: IdempotencyKey, body: SessionRequestDto): SessionResponseDto =
        http.post("v1/session") { call(Call.Account); idempotency(key); setBody(body) }.body()
    suspend fun verifyPlayPurchase(key: IdempotencyKey, body: PlayPurchaseDto): EntitlementDto = /* POST v1/me/purchases/google-play */ TODO()
    suspend fun verifyAppStorePurchase(key: IdempotencyKey, body: AppStorePurchaseDto): EntitlementDto = /* POST v1/me/purchases/app-store (#75) */ TODO()
}
```

Request pipeline (Ktor plugins installed in this order; each request is tagged with a `Call` attribute —
`Public`, `AppCheck`, `LimitedUse`, `Account` — instead of Retrofit annotations):

| # | Plugin | Does |
| --- | --- | --- |
| 1 | `SmHeaders` | `X-App-Version` (one build-number sequence shared by Android `versionCode` and iOS `CFBundleVersion`, so `min_supported_version` applies to both), `Accept-Language` (app language); platform is sent only in `upsertDevice` (`platform: android\|ios`); `X-Installation-Id` **only** on `Call.Account` |
| 2 | `AppCheck` | `X-Firebase-AppCheck` on non-public calls; limited-use token on `Call.LimitedUse` (from `AppCheckPort`: Play Integrity on Android, App Attest with DeviceCheck fallback on iOS) |
| 3 | Ktor `Auth` (bearer) | `Authorization: Bearer` from `AuthPlatform.idToken(forceRefresh = false)` on `Call.Account` only; `refreshTokens { idToken(true) }` once on `401 token_expired`; second 401 → `SessionEvents.signedOut` |
| 4 | `UpgradeRequired` | `426` → `AppEvents.forceUpdate` (→ S41) |
| 5 | `OutageDetector` | 3 consecutive 5xx → `getHealth` → O03 / S42 |
| 6 | `HttpTimeout` + `HttpRequestRetry` | timeouts below; retry only idempotent requests / persisted `Idempotency-Key`, honouring `Retry-After` |
| 7 | `Logging` | debug builds only, headers redacted |

Timeouts tuned for 2G/3G: connect 15 s, request 60 s for the snapshot (streamed into Room with
`Json.decodeSourceToSequence` from `kotlinx-serialization-json-io`, never held in memory as one string), 30 s
otherwise. `Problem` bodies parse into `ApiException(code, status, message, retryable)`.

## 3. Room KMP (`:core:data`, package `core.database`)

**Room KMP vs SQLDelight.** Both run on Android and iOS. Room wins here: v1's entities, DAOs, `Flow` queries,
exported schemas, `AutoMigration`s and migration tests port with small changes (Room KMP needs `@ConstructedBy` and
`SQLiteDriver` instead of `SupportSQLiteOpenHelper`), the team (one developer) already knows it, and KSP generates the
code per target. SQLDelight's SQL-first approach would be a full rewrite of the schema and queries for no functional
gain. Cost of Room KMP: KSP runs for every target (slower iOS builds) and a few Android-only APIs are gone
(`withTransaction` → `useWriterConnection { it.immediateTransaction { … } }`, no `LiveData`, no `Cursor`).

- **Driver (as built): `BundledSQLiteDriver` on both platforms** (`buildSmDatabase` in common code), with
  `setQueryCoroutineContext(Dispatchers.IO)`. Why: one SQLite version everywhere (Android 8 ships SQLite 3.19, iOS a
  much newer one), identical behaviour in `commonTest` on the JVM, Android and the iOS simulator, and it is the path
  Room's KMP docs recommend. Cost: a native SQLite per ABI (≈ 0.7–1 MB download on Android, ≈ 1–1.5 MB on iOS),
  tracked by the size gates. Lever if a budget is exceeded: `AndroidSQLiteDriver` on Android / `NativeSQLiteDriver`
  (system `libsqlite3`) on iOS via an `expect` driver factory — we use no SQLite feature newer than Android 8's (no
  JSON1 functions in queries, no FTS5), so either switch is safe.
- **Location and protection:** Android app-private `databases/` (file-based encryption). iOS: `sm.db` in the **App Group container**
  (`group.in.sarkarimatch.app`, so the P1 Notification Service Extension can share files without a later migration —
  notifications.md §11) with `NSFileProtectionCompleteUntilFirstUserAuthentication`. *As built the iOS DB and
  DataStore files are in `Documents/` (`iosDataModule`), which iCloud device backup includes and the Files app can
  expose if file sharing is ever enabled; FE-10/FE-70 move them to the App Group container before the first TestFlight
  build (no user data exists yet, so no migration)* — **not** `Complete`, because notification actions, BG
  tasks and silent pushes must write while the phone is locked (after first unlock). Same for the DataStore file.
- **Excluded from OS backups on both platforms** (Android `data_extraction_rules.xml`; iOS `NSURLIsExcludedFromBackupKey`
  on the DB, DataStore and key files): restore goes only through our end-to-end-encrypted backup (§7), so a restored
  device never resurrects a stale primary-install flag or reminder ledger.

One database `sm.db`, `exportSchema = true` (schemas in `mobile/core/data/schemas`, used by migration tests on both
platforms), `AutoMigration`s where possible, **no destructive fallback** (the profile and the user's actions exist
nowhere else unless backup is on). Display-only nested content (why-rules cache, what-you-need, timeline links) is
JSON text via `TypeConverter`. `Instant`/`LocalDate` are `kotlinx-datetime` types with converters (epoch millis / ISO
date string).

```kotlin
@Database(entities = [/* … */], version = 1, exportSchema = true)
@ConstructedBy(SmDatabaseConstructor::class)
@TypeConverters(SmConverters::class)
abstract class SmDatabase : RoomDatabase() { /* DAOs */ }
@Suppress("NO_ACTUAL_FOR_EXPECT") expect object SmDatabaseConstructor : RoomDatabaseConstructor<SmDatabase>
// androidMain / iosMain: fun databaseBuilder(): RoomDatabase.Builder<SmDatabase>  (path + driver), bound in Koin
```

```kotlin
// ---- catalog (public data, replaced by batches) ----
@Entity(tableName = "catalog_notices")
data class CatalogNoticeEntity(@PrimaryKey val id: String, val version: Int, @ColumnInfo(name = "catalog_seq") val catalogSeq: Long,
    val status: String, @ColumnInfo(name = "apply_end") val applyEnd: LocalDate?, @ColumnInfo(name = "new_match_paused") val newMatchPaused: Boolean,
    @ColumnInfo(name = "body_json") val bodyJson: String)          // Notice minus posts; LocalizedText maps for the fetched locales

@Entity(tableName = "catalog_posts", indices = [Index("notice_id")])
data class CatalogPostEntity(@PrimaryKey val id: String, @ColumnInfo(name = "notice_id") val noticeId: String,
    @ColumnInfo(name = "rule_version") val ruleVersion: Int, @ColumnInfo(name = "evaluator_min_version") val evaluatorMinVersion: Int,
    @ColumnInfo(name = "exam_tags") val examTags: List<String>, val scope: String, val state: String?,
    @ColumnInfo(name = "rule_json") val ruleJson: String, @ColumnInfo(name = "names_json") val namesJson: String)

// ---- the user's data (device only) ----
@Entity(tableName = "profile")       // single row id=1; also profile_draft (single row) for onboarding/edit
data class ProfileEntity(@PrimaryKey val id: Int = 1, val json: String /* LocalProfile */, val version: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant, val completeness: Float,
    @ColumnInfo(name = "missing_optional") val missingOptional: List<String>)

@Entity(tableName = "matches", indices = [Index("status"), Index("last_date"), Index("notice_id")])
data class MatchEntity(
    @PrimaryKey val id: String,                                   // m_ + post suffix (derived)
    @ColumnInfo(name = "post_id") val postId: String,
    @ColumnInfo(name = "notice_id") val noticeId: String,
    val verdict: String,                                          // eligible | check_yourself
    val status: String,                                           // new|seen|reminded|snoozed|applied|not_interested|expired|withdrawn
    @ColumnInfo(name = "rule_version") val ruleVersion: Int,
    @ColumnInfo(name = "reason_codes") val reasonCodes: List<Int>,
    @ColumnInfo(name = "fee_paise") val feePaise: Long?,          // fee row for this profile; null = n/a, 0 = free
    @ColumnInfo(name = "last_date") val lastDate: LocalDate,      // IST calendar date, never via UTC
    @ColumnInfo(name = "last_date_time") val lastDateTime: String?,
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Instant?,
    @ColumnInfo(name = "not_interested_reasons") val notInterestedReasons: List<String>,
    @ColumnInfo(name = "user_confirmed_at") val userConfirmedAt: Instant?,
    val silent: Boolean,                                          // created by a profile edit → B10, no notification
    @ColumnInfo(name = "matched_at") val matchedAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,      // per-row LWW for backup merge
)

@Entity(tableName = "match_actions")   // append-only local log: undo, backup merge, opt-in counters
data class MatchActionEntity(@PrimaryKey @ColumnInfo(name = "action_id") val actionId: String, @ColumnInfo(name = "match_id") val matchId: String,
    val type: String, @ColumnInfo(name = "occurred_at") val occurredAt: Instant, val source: String, val payload: String?)

@Entity(tableName = "applications", indices = [Index("notice_id")])
data class ApplicationEntity(@PrimaryKey val id: String /* a_ + post suffix */, @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "notice_id") val noticeId: String, @ColumnInfo(name = "post_id") val postId: String,
    @ColumnInfo(name = "applied_at") val appliedAt: Instant, val tracking: String, val region: String?,
    @ColumnInfo(name = "progress_json") val progressJson: String, @ColumnInfo(name = "fee_paid") val feePaid: Boolean?,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant)

/** Generic idempotent write queue for account-level calls only. */
@Entity(tableName = "outbox")
data class OutboxEntity(@PrimaryKey val id: String /* also the Idempotency-Key */, val op: String /* putBackup|putFamilyWatchlist|recordConsent|upsertDevice|reportMatch|postUsageCounters */,
    @ColumnInfo(name = "payload_json") val payloadJson: String, @ColumnInfo(name = "coalesce_key") val coalesceKey: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant, val attempts: Int = 0)

// Also: LifecycleEventEntity, LocalReminderEntity(reminder_id, match_id, kind, fire_at, mechanism, state),
// NotificationLedgerEntity(msg_id, shown_at, source PUSH|LOCAL|BACKUP), InboxEntity (90 days), NotifPrefsEntity,
// RefItemEntity(kind, code, parent, level, name_json, aliases, sort), LanguageEntity, EntitlementEntity(+ token),
// AccountEntity(uid, auth_provider, google_email, billing_ref, is_primary), FamilyLinkEntity, FamilyFollowingEntity
// (+ decrypted watch-list), ConsentRecordEntity(kind, version, value, at), UsageCounterEntity(day, counter, value),
// CatalogStateEntity(locales, seq, snapshot_seq, synced_at, server_time_offset).
```

Volumes: a few hundred catalog notices (≈ 1–3 MB in Room), tens of open matches; plain `Flow<List<…>>` into
`LazyColumn` with `key = id`, no Paging. `CleanupWorker` drops closed notices > 90 days old unless an application
references them, and inbox rows > 90 days.

## 4. DataStore KMP (`:core:data`)

One Preferences DataStore `app_prefs` (not encrypted — nothing secret), created in common code with
`PreferenceDataStoreFactory.createWithPath { platformDir / "app_prefs.preferences_pb" }` (okio path; Android
`filesDir`, iOS the App Group container, same file protection as the DB):

| Key | Type | Use |
| --- | --- | --- |
| `app_language` | one of 13 codes | mirrors the OS per-app locale (Android `AppCompatDelegate`, iOS `AppleLanguages`) for background jobs and notification composition (no UI there) |
| `theme` | system/light/dark | S26 |
| `onboarding_step` | string | S01 resume |
| `privacy_notice_ack` | `{version, at}` | S47; re-shown when `config.privacy_notice_version` differs |
| `pending_deep_link` + `_at` | string, long | replay after onboarding |
| `paywall_moment_last_shown`, `first_applied_done` | instant, bool | B02 rules (first Applied always; then max once / 7 days) |
| `notif_primer_denials` | int | S13 → D01 logic |
| `config_etag`, `reference_version` | string | revalidation |
| `backup_reminders` | bool, default **true** | S28 "बिना इंटरनेट भी याद" / S14 (Android exact-alarm backups; on iOS local notifications are always on, so the toggle is hidden) |
| `ios_window_refilled_at` | instant | iOS pending-notification window bookkeeping (notifications.md §5.2) |
| `crash_reports`, `usage_counters` | bool, default false (opt-in on S47/S38) | Crashlytics collection, `TelemetryWorker` |
| `last_push_received_at`, `last_push_test` | | S29 |
| `sign_in_offer_dismissed_at` | per variant | B16 throttling |

## 5. Local actions (no server round-trip)

```kotlin
interface MatchActionRepository {
    /** One Room transaction: status change + action log + reminder/topic/watch-list side effects. < 50 ms. */
    suspend fun record(matchId: String, action: UserAction, source: ActionSource): RecordedAction
    suspend fun undo(recorded: RecordedAction)
}

sealed interface UserAction {
    data object Seen : UserAction
    data class Applied(val feePaid: Boolean? = null) : UserAction
    data class NotInterested(val reasons: Set<NotInterestedReason> = emptySet()) : UserAction  // NotInterestedInPost | Location | NotPrepared | AlreadyEmployed | NotEligible | Other
    data class ConfirmEligible(val rule: String) : UserAction           // B06
    data class Snooze(val option: SnoozeOption) : UserAction             // Tomorrow | TwoDaysBeforeLastDate | TodayEvening
}
```

`record()` in one Room transaction: update `matches.status`, append `match_actions`, then
- **Applied:** create the local application (timeline from catalog lifecycle events), cancel local reminders and the
  on-device backup alarms, unsubscribe `p_{post_id}`, subscribe `n_{notice_id}` if the cached `Entitlement.token` is
  valid and Pro/trial; if trial unused and not in minor mode, queue B02 (`trial_offer`) else `plans` per the 7-day rule.
- **Not interested:** cancel reminders, unsubscribe `p_{post_id}`; `not_eligible` reason also opens B05.
- **Snooze:** move reminders (never past R-last).
- After commit: schedule the `watchlist` job if a family link is active, and the `backup` job (debounced 10 min) if
  backup is on; on iOS also `ReminderWindow.refill()` (the window must drop the acted-on match's pending requests). Undo (6 s snackbar, or notification "वापस लाएँ") reverts the row from the action log.

Conflicts across devices only arise through the backup (§7): rows merge by `updated_at` (last writer wins per match),
and an `applied` row is never downgraded by an older non-applied row.

## 6. Catalog sync and on-device matching

```kotlin
class CatalogSync(                                     // commonMain, bound in Koin
    private val api: SmApi, private val db: SmDatabase, private val state: CatalogStateDao,
    private val engine: MatchEngine, private val planner: ReminderPlanner, private val topics: TopicSync,
) {
    suspend fun sync(reason: SyncReason): SyncOutcome {
        val locales = languages.chainFor(appLanguage())                      // e.g. "en,hi" or "en,hi,mr"
        val head = api.getCatalogHead(state.etag()).bodyOrCached()
        state.saveServerOffset(head.serverTime)
        var seq = state.seq(locales)
        if (seq == 0L || seq < head.snapshot.seq) {
            api.getCatalogSnapshot(head.snapshot.seq, locales) { src -> db.write { applySnapshot(src.verifying(head.snapshot.sha256[locales])) } }
            seq = head.snapshot.seq
        }
        for (b in head.batches.filter { it.seq > seq }.sortedBy { it.seq }) {
            val changed = db.write { applyBatch(api.getCatalogBatch(b.seq, locales).verify(b.sha256[locales])) }   // useWriterConnection + immediateTransaction
            val diff = engine.evaluate(changed.posts, profile(), todayIst())  // :matching-core, < 10 ms for a batch
            db.write { matches.apply(diff); planner.recompute(diff.affected) }
            topics.sync(diff.affected)                                         // p_{post} / n_{notice}
            notifier.composeFor(diff)                                          // N-01, N-10…, respecting caps/quiet hours
            reminders.syncToOs()                                               // Android alarms/work; iOS pending window
            state.save(locales, b.seq)
        }
        return SyncOutcome.Ok
    }
}
```

- A batch that fails `sha256` or schema parsing is skipped with a non-fatal; the app stays on the previous `seq` and
  retries on the next trigger.
- **Evaluator version:** posts with `evaluator_min_version > MatchEngine.VERSION` evaluate to `check_yourself` with
  the "update the app" line, and S20 shows the soft-update banner.
- **Profile save** (S10, S27): `engine.evaluate(allOpenPosts)` synchronously (< 50 ms) → diff → B10 shows added/removed;
  added matches are `silent` (no notification), removed open matches become `withdrawn`.
- **Language change:** a new locale set means a new snapshot download (CDN-cached per set); until it lands, the UI
  shows the old language's catalog text with fallback.

Triggers. One shared entry point `SyncCoordinator.run(reason)` (a mutex makes concurrent triggers coalesce); only
the trigger plumbing is per platform, behind the `BackgroundScheduler` port:

| Trigger | Android | iOS |
| --- | --- | --- |
| FCM topic `catalog` (`type=sync`) | `PushHandler` → expedited unique work `catalog-sync` (`KEEP`) | background push (`content-available: 1`, APNs priority 5) → `didReceiveRemoteNotification(_:fetchCompletionHandler:)` → `IosEntryPoints.onBackgroundPush` → sync inside the ~30 s budget → completion handler with `.newData/.noData/.failed`. Throttled by iOS and **not delivered after the user force-quits the app** |
| App start / resume | `ProcessLifecycleOwner` ON_START, debounced 5 min | `UIApplication.didBecomeActiveNotification`, debounced 5 min — **the most reliable iOS trigger**; Home shows "अपडेट हो रहा है…" during it |
| Pull-to-refresh (S20, S22) | expedited one-time work; UI shows `refreshing` | direct coroutine in the ViewModel scope (app is foreground) |
| Periodic | daily `ReconcileWorker` (24 h, flex 4 h, no network constraint): sync if online; expire matches; re-arm reminders and backup alarms; age checks | `BGAppRefreshTask` `in.sarkarimatch.app.refresh` re-submitted after each run with `earliestBeginDate` = +4 h (iOS decides when; typically several times a day for a regularly used app, rarely for an unused one): reconcile + sync + refill the notification window, ≤ 25 s. `BGProcessingTask` `in.sarkarimatch.app.catalog` (`requiresNetworkConnectivity`) only when a snapshot (> 100 KB) is pending, e.g. after a language change |
| Notification action | — (receiver writes Room; reconcile handles the rest) | action handler finishes with `ReminderWindow.refill()` (a few ms; no network) |

iOS latency consequence: a new match is found when the app is opened, at the next background refresh, or on a
background push that iOS lets through. The user-visible promise ("new match < 6 h", PRD NFR) holds for users who open
the app or keep Background App Refresh on; S15's iOS variant asks them to keep it on and S29 shows its state
(`UIApplication.backgroundRefreshStatus`). Deadline reminders (R3/R1/R-last, Rp, snooze) do **not** depend on this:
they are scheduled local notifications (notifications.md §5).

### 6a. Offline queue (account writes)

The `outbox` table (§3) is drained by `OutboxDrainer` (common): one request at a time in creation order, coalesced by
`coalesce_key`, reusing the stored `Idempotency-Key`; 4xx other than 408/429 drops the row with a non-fatal.

| | Android | iOS |
| --- | --- | --- |
| Drain triggers | unique work `outbox` with `NetworkType.CONNECTED`, exponential backoff, enqueued after every write | immediately after every write if online (`NWPathMonitor` via `ConnectivityObserver`); on `didBecomeActive`; at the start of every BG refresh/processing task and background push; when connectivity returns while foreground |
| Purchase verification (must not be lost) | `PurchaseVerifyWorker` + `queryPurchasesAsync` on every start | `Transaction.updates` listener at launch + `Transaction.unfinished` sweep on every foreground; a transaction is `finish()`ed only after the server verified it |

User-facing actions never wait on this queue (they are local), so iOS's weaker background execution only delays
backup/watch-list/telemetry uploads, never what the user sees.

## 7. Accounts, backup and restore

```mermaid
sequenceDiagram
  participant App
  participant CM as AuthPlatform (Android: Credential Manager · iOS: GIDSignIn / ASAuthorization)
  participant FA as Firebase Auth (platform SDK)
  participant BS as SecureKeyStore (Block Store · iCloud Keychain)
  participant API
  Note over App: guest: no account, nothing sent
  App->>CM: signIn(Google) — Android GetSignInWithGoogleOption / GetGoogleIdOption (O11); iOS GIDSignIn.signIn<br/>or signIn(Apple) — iOS only: ASAuthorizationAppleIDRequest with SHA-256 nonce (O11a)
  CM-->>App: Google ID token (+ access token on iOS) / Apple identity token + raw nonce
  alt guest already has an anonymous account (bought / joined family)
    App->>FA: currentUser.link(credential) — GoogleAuthProvider / OAuthProvider.appleCredential(idToken, rawNonce, fullName)
    opt FirebaseAuthUserCollisionException
      App->>FA: keep anonymous ID token; signIn(credential)
    end
  else no account yet
    App->>FA: signIn(credential)
  end
  App->>API: startSession {installation_id, locale, privacy_notice_version, merge_anonymous_id_token?, device}
  API-->>App: {user, is_new_user, entitlement, has_backup, primary_installation_id}
  alt has_backup and local profile exists
    App->>App: D10 keep saved (restore) / keep this phone's (overwrite backup)
  else has_backup and no local profile (new phone)
    App->>BS: retrieveBytes(key_id)
    App->>API: getBackup → decrypt → Room → re-evaluate catalog → Home (skip onboarding)
  end
```

- **Auth implementation (decision D7): platform Firebase SDKs behind one port**, not GitLive's
  `firebase-kotlin-sdk`. The common `AuthPlatform` has ~6 calls (`currentUser`, `idToken(force)`,
  `signIn(provider)`, `signInAnonymously`, `link(provider)`, `signOut`, `delete`/`revoke`). Android implements it in
  `androidMain` (Firebase Android + Credential Manager); iOS in Swift (`FirebaseAuth`, `GoogleSignIn`,
  `AuthenticationServices`). GitLive would give a common Firebase API but still needs the Google/Apple UI flows natively
  and links Firebase iOS into the Kotlin framework through cinterop — more build surface than the ~150 lines of Swift.
- **No token storage of our own.** Firebase keeps and refreshes the ID token (Android: app storage; iOS: Keychain).
- **Sign in with Apple (iOS)** is a first-class provider for the same Firebase account model: `startSession` is
  identical; the server sees provider `apple.com`. Apple returns name/email only on the first authorisation; we store
  neither beyond the account header (relay addresses `…@privaterelay.appleid.com` are shown as "Apple ID"). On iOS the
  account header shows "Google" or "Apple"; an Android user signed in with Google uses Google on iOS too (both providers
  can be linked to one Firebase user later; not in P0).
- **Guest → account only when needed:** trial/purchase/family accept with no Google sign-in → B16 first; if the user
  declines, `signInAnonymously()` + `startSession`.
- **Backup (opt-in, Google- or Apple-linked accounts, phone has a screen lock / passcode):** the `backup` job
  serialises profile, matches' statuses + action log tail, applications, prefs, consent records into JSON, encrypts with
  AES-256-GCM (common code; `cryptography-kotlin` with the JDK provider on Android and CryptoKit/CommonCrypto on iOS —
  or an `expect` AES-GCM if that library is not used) using a 256-bit key created once and stored through
  `SecureKeyStore`: Android `BlockstoreClient.storeBytes(… setShouldBackupToCloud(true))`; iOS a Keychain item with
  `kSecAttrSynchronizable = true` + `kSecAttrAccessibleAfterFirstUnlock` (iCloud Keychain, end-to-end encrypted by
  Apple; not readable by us). **Cross-platform restore** (Android → iPhone) cannot reach the Block Store key, so it
  shows "बैकअप नहीं खुल सका — प्रोफ़ाइल फिर से भरें" like a missing key; an optional user-held recovery code is a P1
  idea, not designed. Then
  `putBackup` with `If-Match`; on `412 backup_conflict` → `getBackup`, merge per row by `updated_at`, retry. Key missing
  on a new phone (Block Store not restored) → "बैकअप नहीं खुल सका — प्रोफ़ाइल फिर से भरें" (2 minutes).
- **Primary alert install (B15/N-47):** `startSession.primary_installation_id` and `account` pushes with template N-47
  set `AccountEntity.is_primary`; a non-primary install keeps its data but cancels local reminders and unsubscribes
  `p_`/`n_` topics. B15 "इसी फ़ोन पर पाएँ" → `makeDevicePrimary`.
- **Sign out (D02, signed-in only):** final backup (if on) → `deleteDevice` → `FirebaseAuth.signOut()` → clear account
  state (entitlement, family, account row); **keep** the local profile and matches (the user stays a guest) unless they
  also choose "सब कुछ हटाएँ".
- **Switch account (D11):** backup to the current account → sign out → O11 with account chooser → as above.
- **Delete everything (D03):** guest: typed "हटाएँ" → local wipe (Room, DataStore, alarms/pending notifications,
  scheduled jobs, topics, `deleteToken()`, backup key). Signed in: Google/Apple re-auth → `deleteAccount` (7-day grace,
  `cancelDeletion` from S38) → same local wipe; **Apple-linked accounts also revoke the Apple token**
  on the server (App Store guideline 5.1.1(v)): the app sends `apple_authorization_code` in `startSession` right after
  Sign in with Apple; if `deleteAccount` answers `apple_revocation = token_missing`, the app runs Sign in with Apple
  again and repeats the call with the fresh code. If `Entitlement.source` is a store
  product, show `manage_url` first (Play subscriptions page / `AppStore.showManageSubscriptions`) because deleting the
  account does not cancel a store subscription.
- **Download my data (S38):** writes the local JSON (profile, matches, applications, consents) to a user-chosen file
  (Android SAF `CreateDocument`; iOS share sheet / `UIDocumentPickerViewController` export), plus `getMe` for what the
  server holds.
- Android `android:allowBackup` with `data_extraction_rules.xml` **excluding** Room, DataStore and Firebase files; iOS
  `isExcludedFromBackup` on the same files (restore goes through our encrypted backup only).

Privacy at rest: app-private storage under OS file encryption on both platforms (Android FBE; iOS Data Protection);
no SQLCipher (≈ 3–4 MB per ABI). The profile never appears in logs, Crashlytics keys or analytics.

## 8. Endpoint mapping (repository method → operationId)

| Repository (`:core:data`) | Method | operationId | Cache / queue | Screens |
| --- | --- | --- | --- | --- |
| `ConfigRepository` | `refresh()` / `observe()` | `getAppConfig` | Room+ETag | S01, S41, S42, S47 (notice version), flags everywhere |
| `HealthRepository` | `check()` | `getHealth` | memory 30 s | S01, S42, O03 |
| `CatalogSync` | `sync()` | `getCatalogHead`, `getCatalogSnapshot`, `getCatalogBatch` | Room | all job screens |
| `NoticeRepository` | `fetchShared(noticeId)` | `getNotice` | Room | S21 `/p/{post_id}` not in the local catalog |
| `ReferenceRepository` | `refreshIfStale()`, `observeLanguages()`, `observeQualificationTree()` … | `getReferenceBundle` | Room+ETag | S02, S40, S04–S09, S27 |
| `ProfileRepository` | `observe()`, `save(draft)`, `observeIsMinor()` | — (local + `MatchEngine`) | Room | S04–S10, S26, S27, S36, B10 |
| `MatchRepository` | `observeFeed()`, `observeMatch(id)`, `evaluate(postId)`, `archive(tab)` | — (local) | Room | S20, S21, S25, S44, badge |
| `MatchActionRepository` | `record()`, `undo()` | — (local) | Room | S20, S21, B01–B04, B06, notification actions |
| `ApplicationRepository` | `observeAll()`, `observeTimeline(id)`, `reportProgress()`, `stopTracking()` | — (local, catalog lifecycle) | Room | S22, S23, S37 |
| `NotificationRepository` | `observePrefs()`, `updatePrefs()`, `observeInbox()` | — (local) | Room | S24, S28, B13 |
| `ReportRepository` | `report(postId, ruleVersion, reason, rule, note, withAttributes)` | `reportMatch` | outbox | B05 |
| `UsageCounters` | `increment()`, `flushDaily()` | `postUsageCounters` | Room + outbox | — (opt-in) |
| `PushTestRepository` | `start()`, `observe(id)` | `startPushTest`, `getPushTest`, `ackPushTest` | Room | S16, S29 |
| `SessionRepository` | `ensureAccount(reason)` | `startSession` (+ Firebase SDK) | — | B16, O11, S03, S10, S26, S38, purchase/trial/family accept |
| `AuthRepository` | `signIn(Provider.Google \| Provider.Apple)`, `signOut()`, `switchAccount()` | `AuthPlatform` (Credential Manager / Google Sign-In iOS / Sign in with Apple + Firebase SDK) + `startSession` | — | O11, O11a, D02, D11 |
| `AccountRepository` | `me()`, `delete()`, `cancelDeletion()`, `exportLocal()` | `getMe`, `deleteAccount`, `cancelDeletion` | direct | S38, D03 |
| `ConsentRepository` | `record(purpose)` | `recordConsent`, `listConsents` | outbox | S47 (accounts), backup toggle, family |
| `DeviceRepository` | `upsertIfChanged()`, `unregister()`, `makePrimary()`, `list()` | `upsertDevice`, `deleteDevice`, `makeDevicePrimary`, `listDevices` | outbox / direct | sign-in, token refresh, B15, S38 |
| `BackupRepository` | `enable()`, `disable()`, `uploadNow()`, `restore()` | `getBackup`, `putBackup`, `deleteBackup` | `BackupWorker` | S38, D10, B16 `sync` |
| `EntitlementRepository` | `observe()`, `refresh()` | `getEntitlement` | Room (+ token) | everywhere (Pro gating) |
| `EntitlementRepository` | `products()` | `listProducts` | memory 1 h | S30, B02 |
| `EntitlementRepository` | `startTrial()` | `startTrial` | direct | B02 trial CTA, S30 |
| `EntitlementRepository` | `purchases()`, `verify(StorePurchase)` | `listPurchases`, `verifyPlayPurchase` (Android) / `verifyAppStorePurchase` (iOS, `POST /v1/me/purchases/app-store` — #75) | purchase-verify job / `Transaction.updates` | S30 → S31, S32, app start |
| `ReferralRepository` | `get()`, `redeem(code)` | `getReferral`, `redeemReferral` | memory | S45 |
| `FamilyRepository` (owner) | `list()`, `invite(relation)`, `remove(id)`, `publishWatchlist()` | `listFamily`, `inviteFamily`, `removeFamily`, `putFamilyWatchlist` | `WatchlistWorker` | S34, S48, B12, D06 |
| `FamilyRepository` (member) | `inviteState(token)`, `accept(token)`, `decline(token)`, `following()`, `fetchWatchlist(id)`, `stop(id)` | `getFamilyInvite`, `acceptFamilyInvite`, `declineFamilyInvite`, `listFollowing`, `getFamilyWatchlist`, `stopFollowing` | Room | S35, S49, D12 |
| `AdsGate` | `shouldShow(placement)` | `getAppConfig` (`ads`) + entitlement + minor mode + UMP | Room | S20, S44 (O10) |

Every network call above maps to an operation in `openapi.yaml`; the removed operations (profile, sync, matches,
actions, applications, prefs, inbox, receipts, OTP, WhatsApp, UPI) are listed in `docs/api/README.md` §16 and
[`../contract-alignment.md`](../contract-alignment.md).
