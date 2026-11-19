# Data layer

Contract: `docs/api/openapi.yaml` (v1) + `docs/api/README.md`. Every repository call that touches the network maps to
an `operationId` (§8).

**Founder decisions of 2026-11-19 (ADR-11 in `docs/backend/README.md`):** the eligibility profile, matching,
matches, reminders, actions, applications, inbox and notification preferences live **only on the device**. The app
reads a public, CDN-cached **catalog** and evaluates it locally with `:matching-core` (the same KMP module the server
uses for review previews). The server holds only what an account feature needs (Google sign-in, Pro/trial, family
link, encrypted backup). There is no phone number, OTP, WhatsApp or UPI anywhere.

## 1. Shape

```mermaid
flowchart LR
  subgraph UI[":feature:* (Compose + ViewModel)"]
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
    W[Workers:<br/>CatalogSyncWorker, ReconcileWorker (daily),<br/>ReminderWorker, OutboxWorker, BackupWorker,<br/>WatchlistWorker, TelemetryWorker, PushTestWorker,<br/>PurchaseVerifyWorker, CleanupWorker]
  end
  subgraph DB[":core:database (Room)"]
    T[(catalog_notices, catalog_posts, lifecycle_events,<br/>profile, profile_draft, matches, match_actions,<br/>applications, local_reminders, notif_ledger, inbox,<br/>notif_prefs, ref_items, languages, entitlement, account,<br/>family_links, family_following, consent_records,<br/>usage_counters, outbox, catalog_state)]
  end
  subgraph NET[":core:network"]
    API[SmApi (Retrofit)]
    IC[Interceptors: auth (account calls),<br/>App Check, headers, problem+json]
  end
  FB[(Firebase Auth / App Check /<br/>Installations / FCM topics)]
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
   take effect instantly, offline, from notification buttons without opening the app, and are never sent to the
   server. Only account-level writes (backup, family watch-list, consents, device, reports, counters) are queued.
4. Repositories expose **domain models** from `:core:model`, never DTOs or entities.
5. Dispatchers are injected (`@IoDispatcher`) — no `Dispatchers.IO` literals, so tests can use `StandardTestDispatcher`.
6. **Minor mode** (`ProfileRepository.observeIsMinor()`, DOB < 18 in IST): `AccountRepository`, `EntitlementRepository`
   purchase/trial, `BackupRepository`, `FamilyRepository` (owner side), `ReportRepository.attributes`,
   `UsageCounters` and the ads gate are hard-disabled in the repository layer, not just hidden in UI.

## 2. Network module (`:core:network`)

- **Retrofit 3 + OkHttp 5 + kotlinx.serialization** (`Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }`).
- DTOs hand-written in `com.sarkarimatch.core.network.dto`, mirroring OpenAPI schemas 1:1 (≈ 30 classes; the catalog's
  `RuleDocument` is parsed by `:matching-core`'s own serializer). A CI job diffs DTO field names against `openapi.yaml`.
- `LocalizedText` is a `Map<LanguageCode, String>` with a custom serializer; unknown keys are kept (forward
  compatibility for languages added later).
- Unknown enum values map to the fallbacks the spec documents (push `type` unknown → `sync`).

```kotlin
interface SmApi {
    // public, CDN (no auth, no App Check)
    @GET("v1/catalog/head") suspend fun getCatalogHead(@Header("If-None-Match") etag: String?): Response<CatalogHeadDto>
    @GET("v1/catalog/snapshots/{seq}") suspend fun getCatalogSnapshot(@Path("seq") seq: Long, @Query("locales") locales: String): ResponseBody   // streamed
    @GET("v1/catalog/batches/{seq}") suspend fun getCatalogBatch(@Path("seq") seq: Long, @Query("locales") locales: String): ResponseBody
    @GET("v1/config") suspend fun getAppConfig(@Header("If-None-Match") etag: String?): Response<AppConfigDto>
    @GET("v1/reference") suspend fun getReferenceBundle(@Header("If-None-Match") etag: String?): Response<ReferenceBundleDto>
    @GET("v1/notices/{id}") suspend fun getNotice(@Path("id") id: String, @Query("locales") locales: String): NoticeDto
    // App Check only, unlinkable (no installation id header)
    @POST("v1/reports") suspend fun reportMatch(@Header("Idempotency-Key") key: String, @Body b: ReportInputDto): MatchReportDto
    @POST("v1/telemetry") suspend fun postUsageCounters(@Header("Idempotency-Key") key: String, @Body b: UsageCountersDto): Response<Unit>
    @POST("v1/push-tests") suspend fun startPushTest(@Header("Idempotency-Key") key: String, @Body b: PushTestInputDto): PushTestDto
    // account calls (ID token)
    @POST("v1/session") suspend fun startSession(@Header("Idempotency-Key") key: String, @Body body: SessionRequestDto): SessionResponseDto
    @PUT("v1/me/backup") suspend fun putBackup(@Header("Idempotency-Key") key: String, @Header("If-Match") version: String?, @Body b: BackupInputDto): Response<BackupDto>
    // … one function per operationId in §8
}
```

Interceptor chain (order matters):

| # | Interceptor | Does |
| --- | --- | --- |
| 1 | `HeadersInterceptor` | `X-App-Version`, `Accept-Language` (app language); `X-Installation-Id` **only** on account calls (annotated `@AccountCall`) |
| 2 | `AppCheckInterceptor` | `X-Firebase-AppCheck` on non-public calls; limited-use token (`getLimitedUseAppCheckToken`) on calls annotated `@LimitedUse` |
| 3 | `AuthInterceptor` | `Authorization: Bearer` from `FirebaseUser.getIdToken(false)` on `@AccountCall` only; never on catalog/config/reference |
| — | `TokenAuthenticator` | on `401 token_expired`: `getIdToken(true)` once, retry; second 401 → `SessionEvents.signedOut` |
| 4 | `UpgradeRequiredInterceptor` | `426` → `AppEvents.forceUpdate` (→ S41) |
| 5 | `OutageInterceptor` | 3 consecutive 5xx → `getHealth` → O03 / S42 |
| 6 | `HttpLoggingInterceptor` | debug builds only, `BASIC` |

Timeouts tuned for 2G/3G: connect 15 s, read 60 s for the snapshot (streamed into Room with a `JsonReader`, never held
in memory as one string), 30 s otherwise. `Problem` bodies parse into `ApiException(code, status, message, retryable)`.

## 3. Room (`:core:database`)

One database `sm.db`, `exportSchema = true`, `AutoMigration`s where possible, **no destructive fallback** (the
profile and the user's actions exist nowhere else unless backup is on). Display-only nested content (why-rules cache,
what-you-need, timeline links) is JSON text via `TypeConverter`.

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

## 4. DataStore (`:core:data`)

One Preferences DataStore `app_prefs` (not encrypted — nothing secret):

| Key | Type | Use |
| --- | --- | --- |
| `app_language` | one of 13 codes | mirrors `AppCompatDelegate` locale for workers and notification composition (no Activity there) |
| `theme` | system/light/dark | S26 |
| `onboarding_step` | string | S01 resume |
| `privacy_notice_ack` | `{version, at}` | S47; re-shown when `config.privacy_notice_version` differs |
| `pending_deep_link` + `_at` | string, long | replay after onboarding |
| `paywall_moment_last_shown`, `first_applied_done` | instant, bool | B02 rules (first Applied always; then max once / 7 days) |
| `notif_primer_denials` | int | S13 → D01 logic |
| `config_etag`, `reference_version` | string | revalidation |
| `backup_reminders` | bool, default **true** | S28 "बिना इंटरनेट भी याद" / S14 |
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
- After commit: enqueue `WatchlistWorker` if a family link is active, and `BackupWorker` (debounced 10 min) if backup
  is on. Undo (6 s snackbar, or notification "वापस लाएँ") reverts the row from the action log.

Conflicts across devices only arise through the backup (§7): rows merge by `updated_at` (last writer wins per match),
and an `applied` row is never downgraded by an older non-applied row.

## 6. Catalog sync and on-device matching

```kotlin
class CatalogSync @Inject constructor(
    private val api: SmApi, private val db: SmDatabase, private val state: CatalogStateDao,
    private val engine: MatchEngine, private val planner: ReminderPlanner, private val topics: TopicSync,
) {
    suspend fun sync(reason: SyncReason): SyncOutcome {
        val locales = languages.chainFor(appLanguage())                      // e.g. "en,hi" or "en,hi,mr"
        val head = api.getCatalogHead(state.etag()).bodyOrCached()
        state.saveServerOffset(head.serverTime)
        var seq = state.seq(locales)
        if (seq == 0L || seq < head.snapshot.seq) {
            db.withTransaction { applySnapshot(api.getCatalogSnapshot(head.snapshot.seq, locales).verify(head.snapshot.sha256[locales])) }
            seq = head.snapshot.seq
        }
        for (b in head.batches.filter { it.seq > seq }.sortedBy { it.seq }) {
            val changed = db.withTransaction { applyBatch(api.getCatalogBatch(b.seq, locales).verify(b.sha256[locales])) }
            val diff = engine.evaluate(changed.posts, profile(), todayIst())  // :matching-core, < 10 ms for a batch
            db.withTransaction { matches.apply(diff); planner.recompute(diff.affected) }
            topics.sync(diff.affected)                                         // p_{post} / n_{notice}
            notifier.composeFor(diff)                                          // N-01, N-10…, respecting caps/quiet hours
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

Triggers (one unique work name `catalog-sync`, `ExistingWorkPolicy.KEEP`):

| Trigger | How |
| --- | --- |
| FCM topic `catalog` (`type=sync`) | enqueue expedited work from `PushHandler` |
| App start / resume | `ProcessLifecycleOwner` ON_START, debounced 5 min |
| Pull-to-refresh (S20, S22) | expedited one-time work; UI shows `refreshing` |
| Daily `ReconcileWorker` (24 h, flex 4 h, no network constraint) | sync if online; expire matches; re-arm reminders and backup alarms; age checks (B14, minor → adult) |

## 7. Accounts, backup and restore

```mermaid
sequenceDiagram
  participant App
  participant CM as Credential Manager
  participant FA as Firebase Auth
  participant BS as Block Store
  participant API
  Note over App: guest: no account, nothing sent
  App->>CM: GetSignInWithGoogleOption (button) / GetGoogleIdOption (O11)
  CM-->>App: Google ID token
  alt guest already has an anonymous account (bought / joined family)
    App->>FA: currentUser.linkWithCredential(GoogleAuthProvider.getCredential(idToken))
    opt FirebaseAuthUserCollisionException
      App->>FA: keep anonymous ID token; signInWithCredential(google)
    end
  else no account yet
    App->>FA: signInWithCredential(google)
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

- **No token storage of our own.** Firebase keeps and refreshes the ID token.
- **Guest → account only when needed:** trial/purchase/family accept with no Google sign-in → B16 first; if the user
  declines, `signInAnonymously()` + `startSession`.
- **Backup (opt-in, Google-linked accounts, phone has a screen lock):** `BackupWorker` serialises profile, matches'
  statuses + action log tail, applications, prefs, consent records into JSON, encrypts with AES-256-GCM using a
  256-bit key created once and stored with `BlockstoreClient.storeBytes(… setShouldBackupToCloud(true))`, then
  `putBackup` with `If-Match`; on `412 backup_conflict` → `getBackup`, merge per row by `updated_at`, retry. Key missing
  on a new phone (Block Store not restored) → "बैकअप नहीं खुल सका — प्रोफ़ाइल फिर से भरें" (2 minutes).
- **Primary alert install (B15/N-47):** `startSession.primary_installation_id` and `account` pushes with template N-47
  set `AccountEntity.is_primary`; a non-primary install keeps its data but cancels local reminders and unsubscribes
  `p_`/`n_` topics. B15 "इसी फ़ोन पर पाएँ" → `makeDevicePrimary`.
- **Sign out (D02, signed-in only):** final backup (if on) → `deleteDevice` → `FirebaseAuth.signOut()` → clear account
  state (entitlement, family, account row); **keep** the local profile and matches (the user stays a guest) unless they
  also choose "सब कुछ हटाएँ".
- **Switch account (D11):** backup to the current account → sign out → O11 with account chooser → as above.
- **Delete everything (D03):** guest: typed "हटाएँ" → local wipe (Room, DataStore, alarms, work, topics,
  `FirebaseMessaging.deleteToken()`, Block Store key). Signed in: Google re-auth → `deleteAccount` (7-day grace,
  `cancelDeletion` from S38) → same local wipe. If `Entitlement.source` is a Play product, show `manage_url` first.
- **Download my data (S38):** writes the local JSON (profile, matches, applications, consents) to a user-chosen file
  via SAF, plus `getMe` for what the server holds.
- `android:allowBackup` with `data_extraction_rules.xml` **excluding** Room, DataStore and Firebase files (restore goes
  through our encrypted backup, never Android Auto Backup).

Privacy at rest: Room in app-private storage under file-based encryption; no SQLCipher (≈ 3–4 MB per ABI). The profile
never appears in logs, Crashlytics keys or analytics.

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
| `AuthRepository` | `signInWithGoogle()`, `signOut()`, `switchAccount()` | Credential Manager + Firebase SDK (+ `startSession`) | — | O11, D02, D11 |
| `AccountRepository` | `me()`, `delete()`, `cancelDeletion()`, `exportLocal()` | `getMe`, `deleteAccount`, `cancelDeletion` | direct | S38, D03 |
| `ConsentRepository` | `record(purpose)` | `recordConsent`, `listConsents` | outbox | S47 (accounts), backup toggle, family |
| `DeviceRepository` | `upsertIfChanged()`, `unregister()`, `makePrimary()`, `list()` | `upsertDevice`, `deleteDevice`, `makeDevicePrimary`, `listDevices` | outbox / direct | sign-in, token refresh, B15, S38 |
| `BackupRepository` | `enable()`, `disable()`, `uploadNow()`, `restore()` | `getBackup`, `putBackup`, `deleteBackup` | `BackupWorker` | S38, D10, B16 `sync` |
| `EntitlementRepository` | `observe()`, `refresh()` | `getEntitlement` | Room (+ token) | everywhere (Pro gating) |
| `EntitlementRepository` | `products()` | `listProducts` | memory 1 h | S30, B02 |
| `EntitlementRepository` | `startTrial()` | `startTrial` | direct | B02 trial CTA, S30 |
| `EntitlementRepository` | `purchases()`, `verifyPlayPurchase()` | `listPurchases`, `verifyPlayPurchase` | `PurchaseVerifyWorker` | S30 → S31, S32, app start |
| `ReferralRepository` | `get()`, `redeem(code)` | `getReferral`, `redeemReferral` | memory | S45 |
| `FamilyRepository` (owner) | `list()`, `invite(relation)`, `remove(id)`, `publishWatchlist()` | `listFamily`, `inviteFamily`, `removeFamily`, `putFamilyWatchlist` | `WatchlistWorker` | S34, S48, B12, D06 |
| `FamilyRepository` (member) | `inviteState(token)`, `accept(token)`, `decline(token)`, `following()`, `fetchWatchlist(id)`, `stop(id)` | `getFamilyInvite`, `acceptFamilyInvite`, `declineFamilyInvite`, `listFollowing`, `getFamilyWatchlist`, `stopFollowing` | Room | S35, S49, D12 |
| `AdsGate` | `shouldShow(placement)` | `getAppConfig` (`ads`) + entitlement + minor mode + UMP | Room | S20, S44 (O10) |

Every network call above maps to an operation in `openapi.yaml`; the removed operations (profile, sync, matches,
actions, applications, prefs, inbox, receipts, OTP, WhatsApp, UPI) are listed in `docs/api/README.md` §16 and
[`../contract-alignment.md`](../contract-alignment.md).
