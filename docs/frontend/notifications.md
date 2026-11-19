# On-device notifications

The product *is* the notification. Everything in this file is the main deliverable, not a side concern. Two rules drive it:

1. **The device owns the schedule; FCM pushes and on-device backup reminders deliver it** (founder decisions 2026-11-19). Matches exist only on the phone (ADR-11), so the app computes every reminder slot itself (`:reminder-core`, same rules as design 03 §0.2). The server sends **topic** pushes it can send without knowing the user — catalog changes (`catalog`), R3/R1/R-last for a post (`p_{post_id}`), lifecycle events (`n_{notice_id}`) — and the device turns them into notifications. For R1 and R-last an **on-device backup** alarm fires 30/15 minutes later if the push did not arrive (design 05 §3.6). Push and backup share the deterministic `reminder_id`.
2. **Every tap on a notification action works offline and does not open the app.** Applied / Not interested / Snooze go BroadcastReceiver → Room. There is no server call: actions never leave the device.

Module: `:core:notifications` (channels, builder, receiver, FCM service, local scheduler, OEM helper). It depends on `:core:data` (repositories) and `:core:model`, never on feature modules. Deep-link intents are built from `DeepLinks` in `:core:model` so this module does not need to know about the nav graph.

---

## 1. FCM message contract (device side)

Contract owner: backend, `docs/api/README.md` §10 and `PushData` in `openapi.yaml`. **All pushes are data-only and carry no text**; the device renders every template in the app language (any of the enabled languages), adds buttons, applies quiet hours, caps and dedupe.

| `type` | Arrives from | Device does |
| --- | --- | --- |
| `sync` (+ `seq`) | topic `catalog`, normal priority | enqueue `CatalogSyncWorker` (expedited) → apply batches → `MatchEngine` → compose N-01 / N-01b / N-10…N-13 / N-11c locally |
| `reminder` (`kind=d_minus_3`), `deadline` (`kind=d_minus_1`, `last_day`) + `post_id`, `reminder_id`, `last_date` | topic `p_{post_id}` (normal / high) | derive `match_id`; if the match is open, not snoozed past it and not yet shown → show N-03 / N-04 / N-05 now, or **hold until the user's reminder time** if it is later (R3/R1); cancel the on-device backup alarm with the same id |
| `lifecycle` + `notice_id`, `event_id`, `stage`, `critical` | topic `n_{notice_id}` | if this install tracks an application for that notice and post/region/progress match → N-20…N-27 (Pro) on `critical`/`job_updates`; on a family member's phone → N-52/N-53 on `family` |
| `account` + `template` | the account's devices | N-31/N-33 trial/Pro, `entitlement` (silent: refresh entitlement), N-47 (go quiet, B15 on next open); `fallback_title/body` if the template is unknown to this app version |
| `family` + `family_event`, `family_link_id` | the account's devices | `sync` → fetch + decrypt watch-list, re-subscribe topics, re-arm family alarms; `accepted` → N-44; `removed` → N-54; `paused` → S49 shows paused |
| `self_test` + `test_id` | one token | show N-41 on `critical`, `ackPushTest` (expedited) |
| unknown | — | treat as `sync` |

Composed **only on the device**: new matches, extension/corrigendum/cancelled, periodic (Rp) and snooze reminders, digest (P1), clash and certificate (P1), the on-device backup reminders, and on a family member's phone N-50/N-51 from `p_{post}` pushes.

```kotlin
@AndroidEntryPoint
class SmMessagingService : FirebaseMessagingService() {
    @Inject lateinit var handler: PushHandler          // :core:notifications
    @Inject lateinit var devices: DeviceRepository

    override fun onMessageReceived(msg: RemoteMessage) {
        // ~10 s budget. Room + NotificationManager only; network goes to WorkManager.
        handler.handle(PushPayload.parse(msg.data))
    }
    override fun onNewToken(token: String) {
        topics.resubscribeAll()                         // topic subscriptions follow the token
        devices.enqueueUpsertIfAccount(reason = "token")
    }
}

class PushHandler @Inject constructor(
    private val ledger: NotificationLedgerDao,  // msg_id/reminder_id, shown_at, source(PUSH|LOCAL|BACKUP)
    private val matches: MatchDao,
    private val gate: DeliveryGate,             // already acted, quiet hours, daily cap, snooze, primary install
    private val composer: NotificationComposer, // templates from string resources + catalog LocalizedText
    private val backup: BackupAlarmScheduler,
    private val work: WorkScheduler,
) {
    fun handle(p: PushPayload) {
        when (p.type) {
            Type.SYNC -> work.enqueueCatalogSync(expedited = true)
            Type.REMINDER, Type.DEADLINE -> {
                val m = matches.byPostId(p.postId) ?: return               // not my match (or family follow → FamilyComposer)
                backup.cancel(p.reminderId)
                if (ledger.alreadyShown(p.reminderId)) return
                when (val d = gate.decide(m, p.kind, now())) {
                    is Decision.Show     -> { composer.show(m, p.kind); ledger.markShown(p.reminderId, Source.PUSH) }
                    is Decision.HoldUntil -> work.enqueueShowAt(p.reminderId, d.at)   // user's reminder time is later
                    is Decision.Suppress, is Decision.Defer -> Unit
                }
            }
            Type.LIFECYCLE -> lifecycle.handle(p)
            Type.ACCOUNT, Type.FAMILY -> account.handle(p)
            Type.SELF_TEST -> { composer.showSelfTest(p.testId); work.enqueueAckPushTest(p.testId) }
            else -> work.enqueueCatalogSync(expedited = false)
        }
    }
}
```

`Decision.Suppress` covers "user already acted", "match withdrawn/expired" and "this install is not the account's primary alert install". There are **no receipts**: the server never learns who received what.

## 2. Channels

Channel IDs are permanent once shipped. **Canonical set (design `05-notifications.md §1`, API `PushData.channel`):** `critical`, `new_match`, `reminders`, `job_updates`, `digest`, `account`, and **`family`** (added 2026-11-19, created only when this phone accepts a family invite). Most notifications are composed on the device, which picks the channel from the type → channel table in `PushData`.

| Channel ID | Name (hi / en) | Importance | Lock screen | Push `type`s | Used for |
| --- | --- | --- | --- | --- | --- |
| `critical` | आखिरी तारीख और ज़रूरी सूचना / Last dates & critical | HIGH (heads-up), vibrate `[0,250,150,250]` | PUBLIC | `deadline`, `lifecycle` with `critical=1`, `self_test` | N-04 (1 day before), N-05 (last day), critical lifecycle (admit card, reschedule, DV slot), N-41 self-test |
| `new_match` | नई नौकरी / New job matches | HIGH | PUBLIC | `match_new`, newly-eligible `corrigendum` | N-01, N-01b, N-11c |
| `reminders` | याद दिलाना / Reminders | DEFAULT | PUBLIC | `reminder` | N-02 every-3-days, N-03 (3 days before), snooze due, action confirmations |
| `job_updates` | नोटिस में बदलाव / Notice changes | DEFAULT | PUBLIC | `extension`, `corrigendum`, `cancelled`, non-critical `lifecycle`, `clash`, `certificate` | N-10–N-13, N-20, N-23–N-26, N-28, N-29 |
| `digest` | रोज़ का सारांश / Daily digest | LOW, silent | PUBLIC | `digest` | N-30 |
| `account` | अकाउंट और Pro / Account & Pro | LOW | PRIVATE | `account`, `family` link events | N-31–N-33, N-40, N-42–N-47, N-54, expedited-work FGS on API < 31 |
| `family` | परिवार के अलर्ट / Family alerts | HIGH | PRIVATE | family member's phone only | N-50–N-53 (no actions) |

Unknown `channel` → `reminders`. Channels are created on every app start (idempotent, cheap); names follow the app language and are re-created with the same IDs on language switch.

Grouping (design): job pushes use group `jobs`, with an `InboxStyle` summary "{n} नौकरियाँ आपका जवाब माँग रही हैं" once ≥ 2 are visible (explicit summary because OEM skins don't always auto-bundle); lifecycle pushes use group `forms`. Notification ID = stable hash of `match_id` (+ `stage` for lifecycle), tag = payload `collapse`, so a newer reminder replaces the older one.

On every resume we read `NotificationManagerCompat.areNotificationsEnabled()` and each channel's importance; this drives banner O02 on S20 and the S29 checklist (local only). With the user's opt-in, the daily usage counters include `notif_blocked` (no identifier), which feeds the "< 15 % disable notifications" metric.

## 3. Notification anatomy

```
[icon] Sarkari Match · नई नौकरी · 2 घंटे पहले
SSC CGL 2026 — आप योग्य हैं ✓
आखिरी तारीख 3 दिसंबर · फीस ₹0 (OBC महिला) · 12 दिन बाकी
[ भर दिया ]  [ बाद में ]
```

- `setStyle(BigTextStyle)` with the "why you match" first line and the fee for *their* category.
- Small icon monochrome vector; no large icon bitmap (saves memory and APK).
- `setOnlyAlertOnce(true)` when updating an existing match notification.
- Tag = payload `collapse` (`match_id`), id = constant per type. A reminder for the same match **replaces** the previous one instead of stacking.
- `setGroup("jobs")` + a summary notification when ≥ 2 match notifications are visible (Android 7+ bundles automatically, but OEM skins need the explicit summary).
- `setCategory(CATEGORY_REMINDER)`; `setTimeoutAfter` = end of last date (IST) so stale reminders disappear.
- Footer text: "आधिकारिक सरकारी ऐप नहीं" is shown on the detail screen, not in the notification (length). The source link is in the detail.
- On-device backup reminders use the same N-04/N-05 templates with the suffix "(फ़ोन से याद दिलाया)" / "(reminder from your phone)".
- Text length: every enabled language must fit titles ≤ 45 and action labels ≤ 15 characters (design 05 §7); Tamil/Malayalam strings are the longest, Urdu is RTL (the system lays out the notification by the app locale).
- Content intent: payload `link`, e.g. `sarkarimatch://job/{match_id}?src=push&msg=…&nid=N-04` (route S21 `job/{matchId}`; `sarkarimatch://match/{id}` kept as an alias) via `PendingIntent.getActivity` with `TaskStackBuilder` so Back goes to the Matches feed (see navigation.md).

### Actions (per design templates N-01…N-05)

The payload's `actions` list decides which buttons appear; labels ≤ 15 characters.

| Template | Actions |
| --- | --- |
| N-01 new eligible match, N-02/N-03 reminders | भर दिया · बाद में |
| N-01b check-yourself match | देखें (opens S21, B06) |
| N-04 one day before | भर दिया · रुचि नहीं (no snooze) |
| N-05 last day | साइट खोलें · भर दिया (no snooze) |
| lifecycle (admit card, result…) | देखें / PDF देखें |

| Action | Behaviour |
| --- | --- |
| भर दिया / Applied | broadcast `ACTION_APPLIED` → marks directly (no B01 confirm); notification replaced (same ID, `reminders`, `setTimeoutAfter(10 s)`) by "✓ दर्ज हो गया: {job}" + free/Pro line; B02 paywall moment is queued for the next app open. |
| बाद में / Later | broadcast `ACTION_SNOOZE` with `tomorrow` (09:00 IST); replaced by "ठीक है — कल सुबह 9 बजे फिर याद दिलाएँगे". The other options ("2 days before last date" when ≥ 4 days are left, `today_evening` 19:00 when 2–3 days are left) are chosen in-app (B04). N-04/N-05 have no snooze button. |
| रुचि नहीं / Not interested | broadcast `ACTION_NOT_INTERESTED`; replaced by "छुपा दिया: {job}" with a **वापस लाएँ** action (`ACTION_UNDO`) for 10 s → deletes the pending action or enqueues `undo`. |
| साइट खोलें / Open apply site | `PendingIntent.getActivity` (no trampoline) to a tiny transparent `ApplySiteActivity` that fires `ACTION_VIEW` on the payload's `apply_url` (fallback: cached `match_detail.links` kind `apply`; else opens S21). Logs `apply_site_open{src=push}`. |
| देखें / PDF देखें | content-style `getActivity` deep link (S21 / S23). |

Android 12+ forbids starting an activity from a BroadcastReceiver launched by a notification, so actions that need UI use `getActivity` directly; the three state-change actions do not need UI and use `getBroadcast`.

## 4. Action handling (offline, local only)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant N as Notification
    participant R as ActionReceiver
    participant DB as Room
    participant FCM as FirebaseMessaging
    participant WM as WorkManager
    U->>N: taps "भर दिया"
    N->>R: PendingIntent.getBroadcast(ACTION_APPLIED, matchId, reminderId)
    R->>R: goAsync() (≤ 10 s budget)
    R->>DB: tx: matches.status=applied, match_actions += {action_id, applied, occurred_at, source=notification_action},<br/>applications += a_{post}, local_reminders cancelled, backup alarms cancelled
    R->>FCM: unsubscribe p_{post_id}; subscribe n_{notice_id} if Pro/trial token valid
    R->>N: replace with "✓ आवेदन दर्ज" (auto-dismiss)
    R->>WM: (if family link) WatchlistWorker · (if backup on) BackupWorker, debounced
    R-->>N: finish()
```

- `goAsync()` + a short Room transaction (< 50 ms on a low-end phone) keeps us inside the receiver's budget. Topic (un)subscription is a fire-and-forget Play services call; failures are retried by the daily reconcile, which recomputes the wanted topic set.
- Undo: in-app snackbar "वापस लाएँ" (6 s, B03) or the notification's undo action reverts from the action log.
- After *भर दिया* from a notification, B02 (`trial_offer` while the trial is unused and not in minor mode, else `plans`) is queued in DataStore and shown on the next app open. The trial starts only if the user taps the B02 CTA (`startTrial`).

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
                actions.record(matchId, action, source = ActionSource.NOTIFICATION)   // local only
                notifier.onActionRecorded(matchId, action)
            } finally { pending.finish() }
        }
    }
}
```

## 5. Local reminders and the on-device backup

Why: Chinese-brand phones (Xiaomi, Vivo, Oppo, Realme) often block FCM for apps that are not "autostart"-enabled (PRD top risk), and the server cannot push per-user reminders it does not know about.

- Table `local_reminders(reminder_id, match_id, kind, fire_at, mechanism, state)` is filled by `ReminderPlanner` (`:reminder-core`) for every open match: R0 at evaluation, Rp every 3 days, R3/R1 at the user's reminder time, R-last 08:00 IST, snooze targets, shifted out of quiet hours. Id `r:{match_id}:{kind}:{YYYYMMDD IST}` — the server's topic pushes carry the same id.
- Mechanisms:
  - **Rp, R3, snooze:** WorkManager `OneTimeWorkRequest` with `setInitialDelay` (inexact, survives reboot, no permission). R3 also arrives as a normal-priority topic push at 19:00 and is held until the user's time.
  - **On-device backup for R1 (T + 30 min) and R-last (08:15)** — design 05 §3.6, **P0, on by default** (S28 "बिना इंटरनेट भी याद"): `AlarmManager.setExactAndAllowWhileIdle` when `canScheduleExactAlarms()` (the user granted `SCHEDULE_EXACT_ALARM` via S14, which is now P0), else `setAndAllowWhileIdle` (±15 min in Doze). We declare `SCHEDULE_EXACT_ALARM` (user-grantable, denied by default for new installs on Android 14+), **never** `USE_EXACT_ALARM`. We listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` and fall back to inexact if revoked. The backup is cancelled as soon as the push with that id is shown.
  - Only the next **3** alarms across all matches are registered with the OS (re-armed after each fires).
  - `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` receivers re-arm alarms.
- When a local reminder fires it goes through the same `DeliveryGate` with `Source.LOCAL`/`BACKUP`; dedupe by `reminder_id` in the ledger.
- A daily `ReconcileWorker` (24 h, flex 4 h, `requiresBatteryNotLow=false`, network not required) recomputes reminders and the wanted topic set, expires matches and runs the catalog sync when online. This is the "heartbeat".
- A non-primary install of a signed-in account (B15/N-47) keeps no reminders.

## 6. Quiet hours, daily cap, snooze (device-side mirror of design 03 §0.2)

The **device** schedules every slot (R0, Rp, R3, R1, R-last, lifecycle, corrigendum; ranks 1–6; daily cap 3; quiet hours; collapse) with `:reminder-core`, and `DeliveryGate` applies the same rules to pushes as they arrive. Prefs (quiet hours, reminder time, digest time, backup reminders) are local (Room), edited in S28; they are included in the encrypted backup when it is on.

| Order | Rule | Result |
| --- | --- | --- |
| 1 | User already acted for this match (Room status or pending action) | Suppress |
| 2 | Notifications/channel blocked | nothing shown; O02 banner + S29 |
| 3 | Same match already notified today (IST) and this is not higher-ranked | Replace silently (`setOnlyAlertOnce`) |
| 4 | Quiet hours (default 22:00–07:00 IST) | Defer to quiet-hours end, **except ranks 1–3** (R-last, R1, critical lifecycle) whose slot is within 24 h |
| 5 | Daily cap: 3 pushes per IST day | Ranks 1–3 always shown (may exceed cap); others deferred to digest (`digest` channel) |
| 6 | Snoozed until T | Defer Rp/R3 to T; **R1 and R-last are never suppressed** |

Rank comes from `kind` (1 R-last, 2 R1, 3 critical lifecycle, 4 new match / extension / corrigendum, 5 R3 / snooze due, 6 Rp). The user's reminder time (default 19:00, S28) sets the Rp/R3/R1 slot times; topic pushes that arrive earlier are held until then. Times use `Asia/Kolkata` regardless of device zone, and the server date when the device clock is off by > 10 min.

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

- Onboarding end, S29 "फिर से जाँचें", and weekly: `startPushTest {fcm_token}` (no account needed) → server sends `type=self_test` with `test_id` → `PushHandler` shows N-41 and enqueues `ackPushTest` → the screen observes a local row updated by the handler and, as a fallback, polls `getPushTest` every 5 s. Server times out at 120 s; UI shows "अभी भी इंतज़ार…" after 60 s.
- "Killed app" check (S16 step 3): user swipes the app away, we start the test via WorkManager before they do; result is read on next open.
- On `timeout`, the device computes advice from its own state: `enable_autostart` → S15, `disable_battery_optimization` → battery settings, `unblock_channel` → channel settings, `update_play_services` → Play Store, `enable_backup_reminders` → S14/S28 (design S16 failure path).
- **Weekly check:** a `PeriodicWorkRequest` every `AppConfig.self_test_interval_days` (default 7). Two failed checks, or backup alarms firing without the push for 2 consecutive R1/R-last slots, → local `deliveryHealth = failing` → O02 "अलर्ट शायद नहीं पहुँच रहे" on S20.
- `last_push_received_at` is kept in DataStore for S29.

## 8. Token, topics and devices

- **Topics** are managed by `TopicSync`: `catalog` always (from first launch, including guests and minor mode); `p_{post_id}` for each open match (only on the account's primary install when signed in); `n_{notice_id}` for each tracked application while the cached `Entitlement.token` says Pro/trial; family follows add their own `p_`/`n_` topics. The wanted set is recomputed after every catalog batch, action, entitlement change and in the daily reconcile; the diff is applied with `subscribeToTopic`/`unsubscribeFromTopic`.
- `upsertDevice` (`fcm_token, app_version, locale, device_label`) runs **only for accounts**: on sign-in, `onNewToken` and language change.
- On sign-out: `deleteDevice`, then Firebase sign-out (FCM token and `catalog`/match topics stay — the user is a guest again). On "delete everything": `FirebaseMessaging.deleteToken()` too.

## 9. Primary alert install (B15, N-47)

For signed-in accounts with more than one install, only the **primary** install reminds (otherwise a restored second phone would double every reminder). The newest restored/signed-in install becomes primary; the server sends N-47 to the others, which cancel local reminders and backup alarms and drop `p_`/`n_` topics but keep their data. On next open `MainViewModel` shows B15 ("आपके अलर्ट अब दूसरे फ़ोन पर आएँगे" + "इसी फ़ोन पर पाएँ" → `makeDevicePrimary` → re-arm). Guests have one install and never see B15.

## 10. Family alerts (Pro, P1)

- **Aspirant side** (S34 → B12 relation + optional nickname → S48): `inviteFamily` returns `invite_url`; the app generates a 256-bit key, appends `#k=<base64url key>` and shows the link and a QR (same URL), shared through the Android sharesheet (O08). After the `family_event=accepted` push (N-44), `WatchlistWorker` builds the watch-list (label, relation, open matches' post/notice ids, short titles, last dates, kinds `one_day_before`, `last_day`, `admit_card`, `dv_schedule`), encrypts it (AES-256-GCM, AAD `link_id‖version`) and `putFamilyWatchlist`; it re-publishes after every relevant local change (new match, Applied, Not interested, expiry). `removeFamily` (D06).
- **Member side** (S35 via App Link or QR; if the app is not installed, the `/f/` web page passes token and key through the Play **install referrer**, never to our server): `getFamilyInvite` → "हाँ, अलर्ट भेजें" → `acceptFamilyInvite` (anonymous account if not signed in, no OTP) → S13 family variant → S49. On `family_event=sync`: `getFamilyWatchlist` → decrypt → subscribe `p_`/`n_` topics → schedule local N-50/N-51 backups. Pushes on those topics become N-50…N-53 on the `family` channel, **no action buttons**, member's quiet hours except N-51/N-53 within 24 h. `stopFollowing` (D12).
- The aspirant's name never reaches the server in clear; it is inside the encrypted watch-list.
