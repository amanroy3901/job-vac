# State management

Pattern: **MVVM with unidirectional data flow (UDF)**.

```
Room Flow ─┐
DataStore ─┼─► ViewModel.combine(...) ─► StateFlow<UiState> ─► Composable (stateless) ─┐
Connectivity┘        ▲                                                                   │
                     └──────────── onAction(UiAction) ◄──────────────────────────────────┘
                     └──► Channel<UiEffect> (navigate, snackbar, launch Play, open URL)
```

Rules (from the Compose stack guidelines we adopted):

| Rule | In practice |
| --- | --- |
| Single source of truth | One `StateFlow<XUiState>` per screen, built with `combine(...).stateIn(viewModelScope, WhileSubscribed(5_000), initial)` |
| Model UI state, not booleans | Sealed top-level state (`Loading / Content / Empty / Error`) + orthogonal fields (`banners`, `sheet`, `refreshing`) |
| Immutable state | `@Immutable` data classes, `kotlinx.collections.immutable` `ImmutableList` for lists (stable for the Compose compiler) |
| Expose immutable | `private val _x = MutableStateFlow(…)`, `val x = _x.asStateFlow()` |
| Lifecycle-aware collect | `collectAsStateWithLifecycle()` everywhere (never `collectAsState`) |
| No business logic in UI | Formatting (dates in IST, ₹, "2 दिन बाकी", verdict text) is done in the VM / `:core:model` formatters and arrives as display-ready strings or `UiText` |
| Event-based navigation | `Channel<UiEffect>(Channel.BUFFERED).receiveAsFlow()`; collected in `LaunchedEffect(Unit)` with `withContext(Dispatchers.Main.immediate)` so no effect is lost during config change |
| `LaunchedEffect` keys | keyed by ids, never `Unit` for data loads |
| Pure composables | `XRoute(vm)` collects + wires; `XScreen(state, onAction)` is stateless, previewable, screenshot-testable |
| DI | `@HiltViewModel` + `hiltViewModel()`; no DI in composables |

## 1. Common building blocks (`:core:model` / `:core:designsystem`)

```kotlin
/** Text that may be a resource or server-provided LocalizedText; resolved in the UI with the current locale. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: ImmutableList<Any> = persistentListOf()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: ImmutableList<Any> = persistentListOf()) : UiText
    data class Server(val text: LocalizedText) : UiText        // map over 13 languages; resolved via languages[].fallback, then en
    data class Raw(val value: String) : UiText
}

/** Freshness of what the screen shows, independent of loading. Drives O01 offline banner. */
@Immutable data class Freshness(val online: Boolean, val syncedAt: Instant?, val stale: Boolean)

@Immutable sealed interface Banner {
    data class Offline(val syncedAt: Instant?) : Banner                       // O01
    data object NotificationsOff : Banner                                      // O02 → S13
    data class DeliveryProblem(val health: DeliveryHealth) : Banner            // O02 → S29
    data class Outage(val message: UiText, val alertsDelayed: Boolean) : Banner // O03
    data class TrialEnding(val daysLeft: Int) : Banner                         // → B09
    data class Corrigendum(val matchId: String) : Banner                       // → B07
}

/** Base for user-data screens: what to show when there is nothing to show. */
@Immutable sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>                      // first load, nothing cached: skeleton
    data class Content<T>(val data: T) : LoadState<T>
    data class Empty(val kind: EmptyKind) : LoadState<Nothing>
    data class Error(val message: UiText, val retryable: Boolean) : LoadState<Nothing>   // only when nothing cached
}
```

**Offline is not an error state.** Because Room is the source of truth, a screen with cached data shows `Content` plus an `Offline` banner; `Error` is used only when there is nothing cached *and* the fetch failed. This is the single most important UX rule for slow/flaky networks.

## 2. Example — S20 Home (MatchesFeed)

```kotlin
@Immutable
data class HomeUiState(
    val feed: LoadState<ImmutableList<FeedItem>> = LoadState.Loading,  // Header | Match | Ad (free tier, last) | FooterDisclaimer — see ads.md §4
    val banners: ImmutableList<Banner> = persistentListOf(),
    val refreshing: Boolean = false,
    val freshness: Freshness = Freshness(online = true, syncedAt = null, stale = false),
    val sheet: HomeSheet? = null,          // B01, B02, B03, B04, B06, B07, B09
    val needsReplyCount: Int = 0,          // tab badge
)

@Immutable data class FeedSection(val kind: SectionKind, val title: UiText, val items: ImmutableList<MatchCardUi>)
enum class SectionKind { LastDayToday, ClosingSoon, New, AwaitingReply, CheckYourself }

@Immutable
data class MatchCardUi(
    val matchId: String,
    val title: String,                    // already localized
    val org: String,
    val verdict: VerdictUi,               // Eligible | CheckYourself (+ icon + word, never colour alone)
    val deadline: DeadlineUi,             // tone: Today/Urgent/Soon/Calm/Closed, label "आज आखिरी दिन" / "3 दिन बाकी"
    val feeLabel: String?,                // "₹0 (आपके लिए)" / "₹100"
    val vacancyLabel: String?,
    val headline: String?,                // "उम्र 24 (सीमा 27, OBC +3)"
    val badges: ImmutableList<BadgeUi>,
    val catalogStale: Boolean,            // catalog not synced for > 24 h (O01 variant); actions are local and never pending
)

sealed interface HomeAction {
    data object Refresh : HomeAction
    data class Open(val matchId: String) : HomeAction
    data class TapApplied(val matchId: String) : HomeAction          // → B01
    data class ConfirmApplied(val matchId: String, val feePaid: Boolean) : HomeAction
    data class TapNotInterested(val matchId: String) : HomeAction    // → B03
    data class ConfirmNotInterested(val matchId: String, val reasons: Set<NotInterestedReason>) : HomeAction
    data class Snooze(val matchId: String, val option: SnoozeOption) : HomeAction
    data class Undo(val token: RecordedAction) : HomeAction
    data class BannerTapped(val banner: Banner) : HomeAction
    data object DismissSheet : HomeAction
    data object AdSlotNearViewport : HomeAction                      // lazy ad load, once per visit (ads.md)
    data object AdClicked : HomeAction
    data class OpenPaywall(val source: PaywallSource) : HomeAction  // e.g. "विज्ञापन हटाएँ" → S30 (src = AdSlot)
}

sealed interface HomeEffect {
    data class OpenJob(val matchId: String) : HomeEffect
    data class OpenPaywall(val source: PaywallSource) : HomeEffect
    data object OpenDeliveryHealth : HomeEffect
    data object OpenNotificationPrimer : HomeEffect
    data class ShowUndo(val message: UiText, val token: RecordedAction) : HomeEffect   // O04, 6 s
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    matches: MatchRepository,
    banners: BannerProvider,
    clock: IstClock,
    private val actions: MatchActionRepository,
    private val sync: CatalogSyncScheduler,
    private val paywallMoment: PaywallMomentController,
    private val mapper: MatchCardMapper,           // formatting lives here, unit-tested
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val sheet = savedState.getMutableStateFlow<HomeSheet?>("sheet", null)
    private val refreshing = MutableStateFlow(false)
    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val state: StateFlow<HomeUiState> = combine(
        matches.observeFeed(),               // Flow<List<Match>> from Room
        banners.observe(),                   // offline / permission / delivery / outage / trial
        matches.observeNeedsReplyCount(),
        clock.istDateTicks(),                // re-buckets sections at IST midnight
        refreshing, sheet,
    ) { list, b, count, today, r, s ->
        HomeUiState(
            feed = if (list.isEmpty()) LoadState.Empty(EmptyKind.AllCaughtUp) else LoadState.Content(mapper.sections(list, today)),
            banners = b.toImmutableList(), refreshing = r, sheet = s, needsReplyCount = count,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onAction(a: HomeAction) {
        when (a) {
            HomeAction.Refresh -> viewModelScope.launch { refreshing.value = true; sync.syncNow(SyncReason.PullToRefresh) /* catalog head → batches → local matching */; refreshing.value = false }
            is HomeAction.Open -> send(HomeEffect.OpenJob(a.matchId))
            is HomeAction.TapApplied -> sheet.value = HomeSheet.ConfirmApplied(a.matchId)
            is HomeAction.ConfirmApplied -> viewModelScope.launch {
                sheet.value = null
                val rec = actions.record(a.matchId, UserAction.Applied(a.feePaid), ActionSource.Feed)   // local Room write, card animates out
                send(HomeEffect.ShowUndo(UiText.Res(R.string.applied_saved), rec))
                paywallMoment.afterApplied()?.let { sheet.value = HomeSheet.PaywallMoment(it) }     // B02 (trial/plans), max 1/7 days
            }
            is HomeAction.ConfirmNotInterested -> viewModelScope.launch {
                sheet.value = null
                val rec = actions.record(a.matchId, UserAction.NotInterested(a.reasons), ActionSource.Feed)
                send(HomeEffect.ShowUndo(UiText.Res(R.string.hidden_undo), rec))
            }
            is HomeAction.Undo -> viewModelScope.launch { actions.undo(a.token) }
            /* … */
            else -> Unit
        }
    }
    private fun send(e: HomeEffect) { _effects.trySend(e) }
}
```

Screen-level states for S20:

| State | Trigger | UI |
| --- | --- | --- |
| `Loading` | first launch, catalog snapshot still downloading | 3 skeleton `MatchCard`s (static when reduced motion) |
| `Content` | ≥ 1 open match | `FeedItem`s in `LazyColumn`, `key = item.key`, `contentType = item class`; free tier: one `FeedItem.Ad` last (before footer) when `AdsGate.shouldShow(FeedBottom)` |
| `Empty(NoMatchesYet)` | profile saved, 0 matches ever | "अभी कोई नौकरी नहीं — नई आते ही बताएँगे" + delivery-health CTA |
| `Empty(AllCaughtUp)` | matches exist but none open | "सब का जवाब दे दिया ✓" + link to S22 |
| `Empty(ProfileIncomplete)` | `profile.missing_optional` blocks verdicts | CTA → S27 |
| `Error` | no catalog yet and the download failed | retry; never shown when cache exists |
| + `Offline` banner | no network | content stays interactive; actions queue (card shows pending clock) |

## 3. Example — S21 Job detail (MatchDetail)

```kotlin
@Immutable
sealed interface JobDetailUiState {
    data object Loading : JobDetailUiState
    data class Content(
        val header: DeadlineHeroUi,                       // verdict chip, title, org, countdown, last date + time
        val status: MatchStatusUi,                        // Open / Applied(on) / NotInterested / Snoozed(until) / Expired / Withdrawn
        val actions: ActionBarUi,                         // which of भर दिया / रुचि नहीं / बाद में are enabled
        val whatYouNeed: WhatYouNeedUi?,                  // null while detail not cached → section skeleton only
        val why: ImmutableList<RuleLineUi>,               // ✓/?/✗ + text + optional source link
        val links: OfficialLinksUi,                       // official PDF, apply site, source fetched/reviewed dates
        val changeLog: ImmutableList<CorrigendumUi>,
        val reminderPlan: ImmutableList<ReminderUi>,      // "हम याद दिलाएँगे: 1 दिसं, 3 दिसं"
        val disclaimer: UiText,
        val freshness: Freshness,
        val sheet: JobSheet? = null,                      // B01 B02 B03 B04 B05 B06 B07
    ) : JobDetailUiState
    data class ReadOnlyEvaluation(val title: String, val verdict: VerdictUi, val why: ImmutableList<RuleLineUi>) : JobDetailUiState // post-id link, no match
    data object NotFound : JobDetailUiState
    data class Error(val message: UiText) : JobDetailUiState   // nothing cached + offline
}

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val matches: MatchRepository,
    private val notices: NoticeRepository,
    private val actions: MatchActionRepository,
    private val receipts: ReceiptQueue,
    private val mapper: JobDetailMapper,
) : ViewModel() {
    private val route = savedState.toRoute<JobDetail>()

    val state: StateFlow<JobDetailUiState> =
        combine(matches.observeMatch(route.matchId), matches.observeDetail(route.matchId),
                matches.observeNoticeFor(route.matchId), connectivity.online) { m, d, n, online ->
            when {
                m == null && !online -> JobDetailUiState.Error(UiText.Res(R.string.offline_no_cache))
                m == null -> JobDetailUiState.Loading
                else -> mapper.content(m, d, n, online)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobDetailUiState.Loading)

    init {
        viewModelScope.launch {
            // Revalidate in background (ETag); a 404 turns into NotFound via a tombstone in Room.
            matches.refreshDetail(route.matchId).onNotFound { tryPostEvaluation() }
            actions.record(route.matchId, UserAction.Seen, ActionSource.MatchDetail)   // coalesced, once per day
        }
        route.src?.let { if (it == "push") receipts.opened(route.matchId) }
    }
}
```

S21 states: `Loading` (only for a shared `/p/` post missing from the local catalog) · `Content` (everything — why-lines, fee for you, what you need — is computed locally from the catalog and the profile, so it renders fully offline) · `Content` + `Offline` banner · `ReadOnlyEvaluation` · `NotFound` · `Error`. Status variants change the action bar: Applied → "✓ फ़ॉर्म भर दिया · 3 दिसं" + "फ़ॉर्म की टाइमलाइन" (→ S23); Expired → actions hidden; Withdrawn → explanation from the corrigendum + "मेरी प्रोफ़ाइल देखें".

## 4. Example — S30 Paywall (+ B02)

```kotlin
@Immutable
data class PaywallUiState(
    val plans: LoadState<ImmutableList<PlanUi>> = LoadState.Loading,    // prices ONLY from Play ProductDetails
    val selectedPlanId: String? = null,                                 // default = highlighted (yearly)
    val entitlement: EntitlementUi,                                     // Free / Trial(endsAt) / Pro(expires) / InGrace / OnHold
    val trialAvailable: Boolean,
    val purchase: PurchaseStep = PurchaseStep.Idle,
    val isGuest: Boolean,                                               // B16 `pro` once before the Play sheet
    val source: PaywallSource,                                          // AfterApplied | LockedRow | TrialEnding | Profile | DeepLink
    val billingAvailable: Boolean = true,                               // false: Play missing/unsupported → "Play Store अपडेट करें" (Play Billing is the only path)
)

@Immutable data class PlanUi(val planId: String, val title: String, val price: String /* "₹199" from Play */,
                             val perMonth: String? /* "₹17/महीना" computed from priceAmountMicros */,
                             val period: UiText, val highlight: Boolean, val features: ImmutableList<String>)

sealed interface PurchaseStep {
    data object Idle : PurchaseStep
    data object LaunchingPlay : PurchaseStep          // button spinner; Play sheet O06 is on top
    data object Verifying : PurchaseStep              // server verify (→ S31)
    data object Pending : PurchaseStep                // Play PENDING (e.g. UPI inside Play) → S31 Pending
    data class Failed(val reason: UiText, val retryable: Boolean) : PurchaseStep
}

sealed interface PaywallAction {
    data class SelectPlan(val planId: String) : PaywallAction
    data object Buy : PaywallAction
    data object Restore : PaywallAction               // → D08
    data object StartTrial : PaywallAction            // B02 trial CTA or S30 "14 दिन मुफ़्त आज़माएँ"; only when trialAvailable
}

sealed interface PaywallEffect {
    data class LaunchBilling(val params: BillingLaunch) : PaywallEffect   // Activity needed → handled by Route
    data class OfferSignIn(val variant: SignInVariant) : PaywallEffect    // B16 `pro` for guests
    data class GoToStatus(val ref: String) : PaywallEffect
}
```

The VM combines `EntitlementRepository.observe()` (Room), `BillingRepository.productDetails(products)` (Play, in-memory, re-queried on resume) and `listProducts` (plan → Play product/base-plan mapping). `Buy` emits `LaunchBilling` because `BillingClient.launchBillingFlow` needs an `Activity`; the Route calls it and the result arrives through `BillingRepository.purchaseUpdates` (a `SharedFlow` owned by the app-scoped billing client, so it survives the Paywall being recreated). See billing.md for the full flow.

**B02 (paywall moment) is not a separate VM**: `PaywallMomentController` (in `:feature:pro`, injected into S20/S21 VMs through an interface in `:core:data`) decides the variant locally from the cached entitlement (`trial_available`; guests count as available), minor mode and the DataStore 7-day rule:

| Situation | B02 variant |
| --- | --- |
| *भर दिया*, trial unused (`trial_offer`); first Applied, or last shown > 7 days ago | "14 दिन मुफ़्त शुरू करें" → (guest: B16 `pro` or anonymous account) → `startTrial` → B08 (guest: Google save CTA); "अभी नहीं" → snackbar, S22 lock rows |
| Minor mode | nothing |
| Trial used, free user (`variant = plans`), last shown > 7 days ago | plans summary → S30 |
| Pro active | nothing |

## 5. Onboarding (S04–S10): one VM across steps

`OnboardingViewModel` is scoped to the `OnboardingGraph` back-stack entry (`hiltViewModel(navController.getBackStackEntry<OnboardingGraph>())`), so S04–S09 share one draft. Every field change writes the draft to Room `profile_draft` (debounced 300 ms) → kill-safe. Validation is local (DOB plausible: age 14–60; at least one exam type; OBC → NCL question). S10 saves the profile to Room and runs `MatchEngine` over the local catalog (< 50 ms); no network and no account are involved. If the first catalog snapshot has not finished downloading (first launch on 2G), S10 shows the skeleton until it lands, then the result; offline with no catalog → "इंटरनेट आने पर नतीजा दिखेगा". A DOB under 18 switches on minor mode (S10 hides the Google save button).

## 6. Cross-screen state

| Concern | Owner | Exposed as |
| --- | --- | --- |
| Pro entitlement | `EntitlementRepository` (Room + signed token) | `Flow<Entitlement>`; `isPro` + feature flags used by S22 lock rows, S23, S34–S37 |
| Account / minor mode | `SessionRepository`, `ProfileRepository.observeIsMinor()` | `Flow<AccountState>` (Guest / Anonymous / Google), `Flow<Boolean>`; gate sign-in, purchase, ads, backup, family, counters |
| App language | `LanguageController` (AppCompatDelegate + DataStore mirror) | `Flow<LanguageCode>` (13 codes; layout direction follows) |
| Connectivity | `ConnectivityObserver` (`ConnectivityManager.registerDefaultNetworkCallback`) | `StateFlow<Boolean>` |
| Remote flags / force update / maintenance | `ConfigRepository` | `StateFlow<AppConfig>` |
| Global events (force update, signed out, outage) | `AppEvents` (`SharedFlow`, app-scoped) | collected by `MainActivity` → navigation |
