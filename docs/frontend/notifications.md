# On-device notifications

The product *is* the notification. Everything in this file is the main deliverable, not a side concern. Two rules drive it:

1. **The server owns the schedule; the device is the fallback.** The backend decides when a reminder is due (PRD reminder table) and sends FCM. The device also keeps a *local mirror* of the next due reminders, so a reminder still fires if FCM is late or the OEM killed the process. The two are de-duplicated by `reminderId`.
2. **Every tap on a notification action must work offline and must not open the app.** Applied / Not interested / Snooze go BroadcastReceiver → Room (optimistic) → WorkManager → API.

Module: `:core:notifications` (channels, builder, receiver, FCM service, local scheduler, OEM helper). It depends on `:core:data` (repositories) and `:core:model`, never on feature modules. Deep-link intents are built from `DeepLinks` in `:core:model` so this module does not need to know about the nav graph.

---

## 1. FCM message contract (device side)

Contract owner: backend, `docs/api/README.md` §10. **All pushes are data-only** (no `notification` block). With a `notification` payload Android would render it itself while the app is in background, and we would lose action buttons, the in-app language choice, quiet hours, local dedupe and receipts. Data messages reach `onMessageReceived` in every app state as long as the process can be started. The server uses `priority=high` only for messages that always produce a visible notification (FCM deprioritises apps whose high-priority messages show nothing).

| key | example | device use |
| --- | --- | --- |
| `type` | `match_new` · `reminder` · `deadline` · `extension` · `corrigendum` · `lifecycle` · `digest` · `clash` · `certificate` · `self_test` · `sync` | template, actions, target screen. **Unknown → treat as `sync`** |
| `msg_id` | `d_01JD…` | dedupe key (also vs local mirror) and `message_id` in receipts |
| `match_id` / `application_id` / `notice_id` | | deep-link target + action target |
| `title_hi`, `title_en`, `body_hi`, `body_en` | | pick by in-app locale; fall back to `hi` |
| `priority` | `high` / `normal` | |
| `channel` | `deadline_critical` · `new_match` · `reminders` · `lifecycle` · `digest` · `system` | Android channel id (table §2); unknown → `system` |
| `actions` | `applied,not_interested,snooze` | which buttons to add |
| `last_date` | `2026-08-01` | "2 दिन बाकी" chip; also `setTimeoutAfter` |
| `collapse` | `m_01JD…` | **notification tag**, so a newer reminder replaces the older one for the same match |
| `sync` | `1` | enqueue a delta sync after showing |

`type=sync` is silent. `type=self_test` carries `test_id` and shows a low-importance "अलर्ट काम कर रहे हैं ✓" notification on `system`.

```kotlin
@AndroidEntryPoint
class SmMessagingService : FirebaseMessagingService() {
    @Inject lateinit var handler: PushHandler          // :core:notifications
    @Inject lateinit var devices: DeviceRepository

    override fun onMessageReceived(msg: RemoteMessage) {
        // ~10 s budget. Room + NotificationManager only; network goes to WorkManager.
        handler.handle(PushPayload.parse(msg.data), appState = AppState.current())
    }
    override fun onNewToken(token: String) = devices.enqueueUpsert(reason = "token")   // upsertDevice via WorkManager
}

class PushHandler @Inject constructor(
    private val ledger: NotificationLedgerDao,  // msg_id, shown_at, source(FCM|LOCAL)
    private val gate: DeliveryGate,             // user already acted, quiet hours, daily cap, snooze
    private val notifier: SmNotifier,
    private val sync: SyncScheduler,
    private val receipts: ReceiptQueue,         // postDeliveryReceipts, expedited for received/displayed
) {
    fun handle(p: PushPayload, appState: AppState) {
        receipts.add(p.msgId, Receipt.RECEIVED, appState)
        if (ledger.alreadyShown(p.msgId)) return                   // local mirror fired first
        when (gate.decide(p)) {
            Decision.Show     -> { notifier.show(p); ledger.markShown(p.msgId, Source.FCM)
                                   receipts.add(p.msgId, Receipt.DISPLAYED, appState) }
            Decision.Defer    -> ledger.deferToDigest(p)            // over 3/day or quiet hours (not last day)
            Decision.Suppress -> Unit                               // user already acted locally (queued action)
            Decision.Blocked  -> receipts.add(p.msgId, Receipt.BLOCKED, appState) // channel/app notifications off
        }
        if (p.sync) sync.enqueueDelta(reason = "push")
        receipts.flushExpedited()
    }
}
```

`Decision.Suppress` matters: if the user tapped *भर दिया* offline and the server has not seen it yet, the server may still send a reminder; the device checks `pending_actions`/`matches.status` and drops it. `BLOCKED` receipts let the server escalate to WhatsApp quickly for Pro users instead of waiting for the 15–60 min window.

**Receipts** (`postDeliveryReceipts`): `received` + `displayed` are flushed by an **expedited** WorkManager job immediately (prompt receipts prevent duplicate WhatsApp escalations); `opened`, `dismissed` (via `setDeleteIntent`), `action_tapped` are batched (≤100) with the next flush.

## 2. Channels

Channel IDs are fixed by the backend contract. Created on every app start (idempotent, cheap). Names/descriptions are localized; on language change we re-create them with the same IDs so names update. Importance cannot be raised by the app after creation, so it is chosen right the first time.

| Channel ID | Name (hi / en) | Importance | Used for | Notes |
| --- | --- | --- | --- | --- |
| `new_match` | नई नौकरी / New job matches | HIGH | first alert for an eligible match, extension, corrigendum | heads-up, sound + vibration |
| `deadline_critical` | आखिरी तारीख / Last-date alerts | HIGH | 3-day / 1-day / last-day reminders | last day bypasses *our* quiet hours (not system DND) |
| `reminders` | याद दिलाना / Repeat reminders | DEFAULT | every-3-days repeats | no heads-up; replaces by tag |
| `lifecycle` | फ़ॉर्म अपडेट / Form updates (Pro) | HIGH | admit card, exam date, result, DV slot, clash, certificate | critical for Pro |
| `digest` | दैनिक सारांश / Daily digest | LOW | P1 digest, overflow beyond 3/day | silent |
| `system` | ऐप और खाता / App & account | DEFAULT | self-test, trial ending, payment status, device switch (B15), expedited-work FGS on API < 31 | |

Groups: `grp_alerts` (new_match, deadline_critical, reminders, lifecycle) and `grp_other` (digest, system).

On every resume we read `NotificationManagerCompat.areNotificationsEnabled()` and each channel's importance; changes go to `upsertDevice` (`notifications_enabled`, `channels_blocked[]`) and drive banner O02 on S20 and the S29 checklist. Analytics event `notif_channel_blocked{channel}` feeds the "<15 % disable notifications" metric.

## 3. Notification anatomy

```
[icon] Sarkari Match · नई नौकरी · 2 घंटे पहले
SSC CGL 2026 — आप योग्य हैं ✓
आखिरी तारीख 3 दिसंबर · फीस ₹0 (OBC महिला) · 12 दिन बाकी
[ भर दिया ]  [ रुचि नहीं ]  [ बाद में ]
```

- `setStyle(BigTextStyle)` with the "why you match" first line and the fee for *their* category.
- Small icon monochrome vector; no large icon bitmap (saves memory and APK).
- `setOnlyAlertOnce(true)` when updating an existing match notification.
- Tag = `match_id`, id = constant per type. A reminder for the same match **replaces** the previous one instead of stacking.
- `setGroup("matches")` + a summary notification when ≥ 2 match notifications are visible (Android 7+ bundles automatically, but OEM skins need the explicit summary).
- `setCategory(CATEGORY_REMINDER)`; `setTimeoutAfter` = end of last date (IST) so stale reminders disappear.
- Footer text: "आधिकारिक सरकारी ऐप नहीं" is shown on the detail screen, not in the notification (length). The source link is in the detail.
- Content intent: `sarkarimatch://job/{jobId}?src=push&push_id=…` (route S21 `job/{jobId}`; `sarkarimatch://match/{id}` kept as an alias) via `PendingIntent.getActivity` with `TaskStackBuilder` so Back goes to the Matches feed (see navigation.md).

### Actions

| Action | Shown when | Behaviour |
| --- | --- | --- |
| भर दिया / Applied | match_new, reminder, deadline | broadcast `ACTION_APPLIED` (marks directly, no B01 confirm; B02 paywall moment is queued for the next app open, per inventory B01). After success, replace the notification with a short "✓ आवेदन दर्ज" for 4 s (`setTimeoutAfter(4000)`). On free tier, it also posts the Pro offer into the app (not a notification) the next time the app opens. |
| रुचि नहीं / Not interested | same | broadcast `ACTION_NOT_INTERESTED`, cancel notification. Reason is not asked from the notification (keep one tap); the app may ask later. |
| बाद में / Later | same, not on last day | broadcast `ACTION_SNOOZE` with default "कल सुबह 9 बजे" (or "आज शाम 6 बजे" if ≤ 2 days left, matching B04); "2 days before last date" is picked inside the app (B04). |
| PDF देखें / Open notice | lifecycle (admit card, result) | opens the official URL in a Custom Tab via a trampoline-free `PendingIntent.getActivity` to `MainActivity` with a deep link (Android 12+ forbids notification trampolines through receivers). |

Android 12+ forbids starting an activity from a BroadcastReceiver launched by a notification, so actions that need UI use `getActivity` directly; the three state-change actions do not need UI and use `getBroadcast`.

## 4. Action handling (offline-safe)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant N as Notification
    participant R as ActionReceiver
    participant DB as Room
    participant WM as WorkManager
    participant API as Backend
    U->>N: taps "भर दिया"
    N->>R: PendingIntent.getBroadcast(ACTION_APPLIED, matchId, msgId)
    R->>R: goAsync() (≤ 10 s budget)
    R->>DB: tx: matches.status=applied, local_override=1 (optimistic),<br/>pending_actions += {action_id=UUID, type=applied, matchId,<br/>occurred_at, source=notification_action}
    R->>DB: local_reminders: cancel all for matchId
    R->>N: replace with "✓ आवेदन दर्ज" (auto-dismiss)
    R->>WM: enqueueUniqueWork("flush-actions", APPEND_OR_REPLACE,<br/>OneTime(ActionFlushWorker), constraint=CONNECTED, expedited)
    R-->>N: finish()
    WM->>DB: read pending_actions ordered by at
    WM->>API: POST /v1/me/actions:batch {actions[≤50]}<br/>Idempotency-Key: batch UUID (stored with the batch)
    API-->>WM: 200 results[] (per action, input order)
    loop each result
        alt ok (applied=true or false)
            WM->>DB: delete pending_action; upsert result.match (authoritative),<br/>result.application, result.entitlement; stash result.paywall
        else error.retryable=false (e.g. match not found)
            WM->>DB: delete pending_action; clear local_override; log non-fatal
        end
    end
    opt whole call fails (network / 5xx / 429)
        WM-->>WM: Result.retry() (exponential, 30 s base, honours Retry-After)
    end
    WM->>WM: action_tapped receipt for msgId
```

Key points:

- `goAsync()` + a short Room transaction (< 50 ms on a low-end phone) keeps us inside the receiver's budget. No network in the receiver.
- **Expedited work** (`setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`) so the server learns about Applied within seconds when online. On Android < 12 expedited work runs as a foreground service, so `ActionFlushWorker.getForegroundInfo()` must return a silent notification on the `account` channel.
- `action_id` (UUID) is generated **once**, at tap time, and stored with the action; it is the server's dedup key, so double-taps and retries are safe. The batch's `Idempotency-Key` is generated when the batch is formed and persisted with it, so a retried batch reuses it (README §5 of the API).
- Undo: inside the app (Snackbar "वापस लाएँ", 6 s per B03) we delete the `pending_action` if it is not flushed yet, else enqueue `type=undo`. The notification does not offer undo.
- Conflict rule (server-defined): actions are applied in `occurred_at` order; a stale action returns `applied=false` with the authoritative `match`, which overwrites the local row (data-layer.md §5).
- If `result.paywall != null` (first *भर दिया* → trial started), it is stored in DataStore and B02/B08 is shown on the next app open, as the inventory specifies for push-action Applied.

```kotlin
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: MatchActionRepository
    @Inject lateinit var notifier: SmNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(ctx: Context, intent: Intent) {
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID) ?: return
        val action = UserAction.fromIntent(intent.action) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                actions.recordLocally(matchId, action, source = ActionSource.NOTIFICATION) // Room tx + enqueue flush
                notifier.onActionRecorded(matchId, action)
            } finally { pending.finish() }
        }
    }
}
```

## 5. Local reminder mirror

Why: Chinese-brand phones (Xiaomi, Vivo, Oppo, Realme) often block FCM for apps that are not "autostart"-enabled. PRD calls this out as a top risk. A local mirror means that whenever our process gets a chance to run (user opens app, any sync, boot), the next reminders are already scheduled on the device.

- Table `local_reminders(reminder_id, match_id, fire_at_utc, kind, state)` is filled from the server's plan: `MatchSummary.next_reminder_at` (in every sync) and `MatchDetail.reminder_plan[]` (when the detail is cached). Where neither exists it is computed on device from the PRD table: every 3 days, D-3, D-1, last-day 08:00 IST, shifted out of quiet hours. Local reminder ids are `local:{match_id}:{fire_at}`; because the server `msg_id` is not known in advance, dedupe against FCM uses `(match_id, IST date, kind)` (Contract gap G-08 asks for a deterministic reminder id).
- Only the next **3** reminders across all matches are actually registered with the OS at any time (re-armed after each fires). This keeps us well below alarm limits and wake-ups.
- Mechanism:
  - **Default: WorkManager `OneTimeWorkRequest` with `setInitialDelay`** (inexact, survives reboot, no permission). Good enough for "every 3 days" and D-3.
  - **Last-day and D-1 morning: `AlarmManager.setAndAllowWhileIdle`** (inexact but fires in Doze within a ~15 min window). This is the default and needs no permission.
  - **Exact-alarm upgrade (P1, opt-in, screen S14):** only if the user turns on "बिना इंटरनेट भी आखिरी दिन का रिमाइंडर" in S28 do we use `setExactAndAllowWhileIdle`, after checking `alarmManager.canScheduleExactAlarms()` and, if false, sending them to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` via S14. We declare `SCHEDULE_EXACT_ALARM` (user-grantable, denied by default for new installs on Android 14+), **never** `USE_EXACT_ALARM` (Play restricts it to alarm/calendar apps). We listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` and fall back to inexact if revoked. A ±15 min window is fine for most users, so this stays optional.
  - `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` + `TIMEZONE_CHANGED` receivers re-arm the 3 registered reminders (alarms are cleared on reboot; WorkManager re-schedules itself).
- When a local reminder fires, it goes through the same `PushHandler` with `Source.LOCAL`; dedupe by `reminder_id` == server `push_id`. If FCM already showed it, local does nothing; if local fires first, the later FCM is dropped.
- A daily `PeriodicWorkRequest` (24 h, flex 4 h, `requiresBatteryNotLow=false`, network not required) re-computes the mirror and runs the delta sync when network exists. This is the "heartbeat".

## 6. Quiet hours, daily cap, snooze

Quiet hours and digest time come from `NotificationPrefs` (synced; edited in S28 via `putNotificationPrefs`, mirrored in Room so the gate works offline). `DeliveryGate` applies (in this order):

1. User already acted (Room) → Suppress.
2. Channel disabled → still ack as "received, channel off" so the server can fall back to WhatsApp for Pro.
3. Quiet hours 22:00–07:00 IST (user-editable in Settings, stored in DataStore) → Defer to 07:00, **except** `urgency = last_day`.
4. Daily cap 3 pushes (rolling IST day) → Defer to digest, except `deadline`/`lifecycle` with `urgency >= high`.
5. Snoozed until T (per match, Room) → Defer to T, but never past last-date morning.

The server applies the same rules; doing it on device too protects against clock drift, offline actions and local-mirror reminders. Times are computed in `Asia/Kolkata` regardless of device zone (users near Nepal border sometimes have wrong zones), using `java.time` (core library desugaring on API 26 is not needed: `java.time` exists from API 26).

## 7. Permissions and OEM reliability

### POST_NOTIFICATIONS (Android 13+)

- Asked inside onboarding's guided permission step (screen in design inventory), **after** a pre-prompt that explains in Hindi with a sample notification image ("ऐसे अलर्ट आएंगे"). Never asked on first launch cold.
- If denied once: Android lets us ask once more. Second time we show "Settings में जाकर चालू करें" with `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.
- Persistent banner on Matches feed while disabled (with dismiss for 7 days).

### Battery optimization and autostart

- `PowerManager.isIgnoringBatteryOptimizations()` checked on resume. We **do not** use `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Play restricts it to specific use cases; alerting apps are a grey area). We open `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (list screen) with instructions instead.
- OEM autostart screens have no public API. We keep a table of known intents and try them in order, falling back to app details settings. Table is shipped in code and can be overridden by Remote Config without an app update (OEMs change component names).

```kotlin
object OemAutostart {
    data class Target(val pkg: String, val cls: String)
    val targets: Map<String, List<Target>> = mapOf(
        "xiaomi" to listOf(Target("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        "redmi"  to listOf(Target("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        "poco"   to listOf(Target("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        "oppo"   to listOf(
            Target("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Target("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            Target("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")),
        "realme" to listOf(Target("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        "oneplus" to listOf(Target("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
        "vivo"   to listOf(
            Target("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Target("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        "iqoo"   to listOf(Target("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        "samsung" to listOf(Target("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
        "huawei" to listOf(Target("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
    )

    /** Returns the first resolvable intent, else app-details settings. Never throws. */
    fun intentFor(ctx: Context): Intent {
        val brand = Build.MANUFACTURER.lowercase()
        targets[brand].orEmpty().forEach { t ->
            val i = Intent().setComponent(ComponentName(t.pkg, t.cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(ctx.packageManager) != null) return i
        }
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
    }
}
```

The guide (S15) shows brand-specific Hindi steps as `OemStepCard`s (icon + text + "सेटिंग खोलें"), no screenshots, so 0 KB of images. Steps and intent actions can be overridden server-side via `AppConfig.oem_guides` (keyed by lowercase `Build.MANUFACTURER`) without an app update; the bundled table above is the fallback. We cannot read whether autostart was granted, so we ask "क्या आपने चालू किया?" and then rely on the self-test.

### Push delivery self-test (S16)

- Onboarding end, S29 "फिर से जाँचें", and weekly: `startSelfTest(installation_id)` → server sends `type=self_test` with `test_id` → `PushHandler` shows it and posts `received`/`displayed` receipts with `message_id = test_id` → the screen observes a local `SelfTestDao` row (updated by the handler) and, as a fallback, polls `getSelfTest` every 5 s. Server times out at 120 s; UI shows "अभी भी इंतज़ार…" after 60 s.
- "Killed app" check (S16 step 3): user swipes the app away, we start the test via WorkManager before they do; result is read on next open.
- On `timeout`, render `SelfTest.advice[]` (`enable_autostart` → S15, `disable_battery_optimization` → battery settings, `unblock_channel` → channel settings, `update_play_services` → Play Store, `link_whatsapp` → S33).
- **Weekly check:** a `PeriodicWorkRequest` every `AppConfig.self_test_interval_days` (default 7) calls `startSelfTest`. `Device.delivery_health = degraded|failing` → banner O02 "अलर्ट शायद नहीं पहुँच रहे" on S20.
- `last_push_received_at` is kept in DataStore for S29.

## 8. Token & device lifecycle

- `upsertDevice(installation_id)` (`DeviceInput`: `fcm_token, app_version, os_api_level, manufacturer, model, notifications_enabled, channels_blocked[], battery_optimization_ignored, autostart_confirmed, timezone`) is sent by a unique WorkManager job on: first session, `onNewToken`, after the S13/S15 guide, and on app start **only if** a hash of those fields changed (stored in DataStore). This doubles as the telemetry the server needs for WhatsApp escalation.
- On logout: `deleteDevice`, then `FirebaseMessaging.deleteToken()`, then Firebase sign-out.

## 9. Primary alert device (B15)

Only the **primary** device receives alert pushes; the newest signed-in device becomes primary automatically, others get `sync` pushes only (API README §3). `upsertDevice` returns `Device.primary`. When `false`: the device disables its local reminder mirror (so the user is not reminded twice), and on next open `MainViewModel` shows B15 ("आपके अलर्ट अब दूसरे फ़ोन पर आएँगे" + "इसी फ़ोन पर पाएँ" → `makeDevicePrimary` → re-arm mirror).

## 10. Family alerts (Pro, P1)

- Aspirant side (S34/B12): `inviteFamily` returns `invite_text` + `invite_link` → Android sharesheet (WhatsApp first). `listFamily` / `removeFamily` (D06).
- Invitee side (S35, App Link `https://sarkarimatch.in/f/{token}`): `getFamilyInvite(token)` → OTP (`purpose=family_accept`, S11/S12 reused) → `acceptFamilyInvite(token, channels=[push, whatsapp?], consent_version)`. Invitees have no profile; the app opens into a minimal "family mode" home (list of the aspirant's critical dates from their inbox).
- Family pushes use the `lifecycle`/`deadline_critical` channels with **no action buttons** (family members can't mark Applied). If 05-notifications.md defines a separate `family` channel ID, add it here; the backend channel list currently has none.
