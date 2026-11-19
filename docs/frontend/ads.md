# Ads (free tier, light)

PRD (Pricing, decided 2026-11-19): free tier shows **one small native ad** at the bottom of the matches feed (S20) and of the daily digest (S44). Pro (any source, including trial and referral) removes ads. Never in reminders, push/WhatsApp/SMS, match detail (S21), the "what you need" card, "why you match" lines, sheets, onboarding, paywall, S22/S23. No interstitial, app-open, rewarded or video ads.

Design: `06-components.md §J1 NativeAdSlot`, inventory O10. API: `AppConfig.ads` + `Entitlement.ads_enabled` (API README §13).

## 1. SDK choice

| Option | Verdict |
| --- | --- |
| **Google Mobile Ads "Next-Gen" SDK** (`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk`, 1.x) | **Chosen.** Google's preferred Android SDK since mid-2026 (classic `play-services-ads` 25.x is now "legacy"), native ads supported, faster init, background-thread initialisation. Version to pin: latest stable at implementation time (1.6.0 seen in Oct 2026 release notes — **verify**). |
| Classic `play-services-ads` 25.x | Fallback if a Next-Gen gap blocks us; same architecture (only `:core:ads` changes). |
| Other networks / mediation | Not at launch: each adapter adds size and policy review; India fill on AdMob is adequate for 1 small slot. |

**Consent SDK:** Google **UMP** (`com.google.android.ump:user-messaging-platform`, 3.x; 3.2.0 is the version Next-Gen pulls in — **verify**). In India no GDPR/US-state message is normally required, so UMP usually returns `NOT_REQUIRED`; we still call it so travelling/EEA users are handled correctly, and so a DPDP-oriented message can be switched on from the AdMob console later without an app release.

## 2. Module and lazy initialisation

New module **`:core:ads`** (only `:feature:jobs` and `:app` depend on it). The ads SDK is **never touched at cold start**:

```kotlin
interface AdsGate {                                   // in :core:data (no SDK types)
    /** true only if config.ads.enabled && placement listed && entitlement.ads_enabled
     *  && sessions > hide_on_first_sessions && appVersion >= min_app_version && !userUnder18 && consentResolved. */
    fun shouldShow(placement: AdPlacement): Flow<Boolean>
}

interface NativeAdLoader {                            // in :core:ads (wraps the SDK)
    suspend fun load(placement: AdPlacement): AdLoadResult   // Loaded(handle) | NoFill | Error
    fun release(handle: AdHandle)
}

@Singleton
class GmaNativeAdLoader @Inject constructor(
    @ApplicationContext private val ctx: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val consent: AdConsentManager,
) : NativeAdLoader {
    private val init: Deferred<Unit> = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        consent.ensureResolved()                      // UMP requestConsentInfoUpdate (+ form only if required)
        MobileAds.initialize(ctx, InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).build()) {}  // Next-Gen API shape: verify
    }
    override suspend fun load(placement: AdPlacement): AdLoadResult {
        init.await()                                  // first call happens only when the slot is about to scroll in
        /* NativeAdLoader with request config: non-personalised unless consented, max content rating "G" or "PG",
           tagForUnderAgeOfConsent when profile age < 18 (we don't show ads then anyway) */
        TODO()
    }
}
```

- Manifest: disable the SDK's automatic init/optimisations at start where supported (`com.google.android.gms.ads.flag.OPTIMIZE_INITIALIZATION` / `OPTIMIZE_AD_LOADING` metadata for the classic SDK; Next-Gen initialises only when we call it). Remove the ads `AD_ID` permission (`tools:node="remove"` on `com.google.android.gms.permission.AD_ID`) — we don't need the ad id for non-personalised ads and it keeps the Data Safety form simple.
- First trigger: `LazyListState` shows an index ≥ lastIndex − 2 on S20/S44 **and** `AdsGate.shouldShow` is true. Pro users never load the SDK classes at all (not even `MobileAds.initialize`).
- One request per screen visit; the loaded ad is cached in the `HomeViewModel` for the life of the screen and destroyed in `onCleared()` (native ads hold views/bitmaps — matters on 2 GB phones). No preloading at start, no refresh timer.

## 3. Entitlement and consent rules

```kotlin
class DefaultAdsGate @Inject constructor(
    config: ConfigRepository, entitlement: EntitlementRepository, profile: ProfileRepository,
    sessions: SessionCounter, consent: AdConsentManager,
) : AdsGate {
    override fun shouldShow(placement: AdPlacement) = combine(
        config.observe(), entitlement.observe(), profile.observeAgeYears(), sessions.count, consent.state,
    ) { c, e, age, n, cs ->
        val ads = c.ads ?: return@combine false
        ads.enabled && placement.wire in ads.placements &&
            e.adsEnabled &&                                        // server: false for Pro incl. trial/referral
            (ads.minAppVersion == null || BuildConfig.VERSION_CODE >= ads.minAppVersion) &&
            n > ads.frequency.hideOnFirstSessions &&               // default 3: no ads during first sessions
            (age == null || age >= 18) &&
            cs != AdConsent.Unknown                                // UMP resolved (NOT_REQUIRED counts)
    }.distinctUntilChanged()
}
```

- **Entitlement is the server's `Entitlement.ads_enabled`** (Room-cached), never a local `isPro` guess. Buying Pro or starting the trial flips it through the verify / action result / sync, and the slot disappears immediately (state recomputes → `AdSlotState.Hidden`).
- **DPDP:** ads are served non-personalised by default; the app does not send any profile data (DOB, category, state, qualification) to the ad SDK — no custom targeting keys. The privacy policy and Data Safety form list "Advertising (Google AdMob)" with device identifiers as *collected by the SDK*. A separate consent purpose `ads_personalisation` would be needed before ever turning on personalised ads (Contract gap G-15) — default stays off.
- **Kill switch:** `AppConfig.ads.enabled = false` (fetched ≤ 15 min) hides every slot and stops loads; `min_app_version` disables a bad SDK build.

## 4. The ad slot as a feed item

The feed is a list of sealed items, so the ad slot is one more `contentType` — matches never shift when it loads or collapses because it is always last.

```kotlin
@Immutable sealed interface FeedItem {
    val key: String
    data class Header(val section: SectionKind, val title: UiText) : FeedItem { override val key = "h:$section" }
    data class Match(val card: MatchCardUi) : FeedItem { override val key = "m:${card.matchId}" }
    data class Ad(val placement: AdPlacement, val state: AdSlotState) : FeedItem { override val key = "ad:${placement}" }
    data object FooterDisclaimer : FeedItem { override val key = "footer" }
}

@Immutable sealed interface AdSlotState {
    data object Loading : AdSlotState                  // static placeholder with only the "विज्ञापन · Ad" pill (no shimmer)
    data class Loaded(val ad: NativeAdUi) : AdSlotState // NativeAdUi wraps an opaque AdHandle (stable key, not recomposed)
    data object NoFill : AdSlotState                   // collapses to 0 height
    data object Hidden : AdSlotState                   // Pro / gated off → item not emitted at all
}
```

`HomeViewModel` builds `items = sections.flatMap { header + matches } + (Ad if shouldShow) + FooterDisclaimer` (§J1: ad before the footer disclaimer). `HomeScreen`:

```kotlin
LazyColumn(state = listState) {
    items(state.items, key = { it.key }, contentType = { it::class }) { item ->
        when (item) {
            is FeedItem.Header -> SectionHeader(item.title)
            is FeedItem.Match -> MatchCard(item.card, onAction)
            is FeedItem.Ad -> NativeAdSlot(item.state, onAdClick = { onAction(HomeAction.AdClicked) },
                                           onRemoveAds = { onAction(HomeAction.OpenPaywall(PaywallSource.AdSlot)) })
            FeedItem.FooterDisclaimer -> DisclaimerNote()
        }
    }
}
LaunchedEffect(listState) {
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
        .map { it >= state.items.lastIndex - 2 }.distinctUntilChanged().filter { it }
        .collect { onAction(HomeAction.AdSlotNearViewport) }      // VM loads once per visit
}
```

`NativeAdSlot` (in `:core:ads`, styled with `:core:designsystem` tokens per §J1: grey `surfaceContainer`, dashed outline, "विज्ञापन · Ad" pill, "विज्ञापन हटाएँ" → S30, outlined compact CTA, AdChoices corner) hosts the SDK's `NativeAdView` through `AndroidView`, with headline/body/icon/CTA views registered as the SDK requires. TalkBack: separate stop with "विज्ञापन:" prefix. S44 Digest uses the same item type with `AdPlacement.Digest`.

## 5. Size, startup and performance impact

- Budget: ads SDK + UMP are the single largest addition (~1.5–3 MB download, to be measured). The CI size gate is raised from 8 MB to **≤ 10 MB** download only after measuring; if it exceeds that, fall back to the lighter of the two SDKs or a WebView-free template. Still far under the PRD's 15 MB.
- Startup: zero cost (lazy, no ContentProvider init, Pro users never load it). Baseline profile does **not** include ad paths.
- Memory: one native ad alive at a time, destroyed with the screen; no media view (no video decoder).
- Network: the ad request is skipped when `ConnectivityObserver` reports offline or `isActiveNetworkMetered && dataSaver on` (`RESTRICT_BACKGROUND_STATUS_ENABLED`) — the user's alerts matter more than the ad.

## 6. Content safety

AdMob console blocks (primary): coaching/test-prep, "sarkari result"-style sites, loan apps, gambling, dating, plus sensitive categories in `AppConfig.ads.blocked_categories`. Max ad content rating "PG". Overflow "यह विज्ञापन ठीक नहीं" logs `ad_report{creative_id}` for manual blocking. The slot is visually unlike a `MatchCard` so an ad can never be mistaken for a job or an official notice.

## 7. Tests

- `DefaultAdsGateTest`: Pro/trial/referral → false; kill switch; placement not listed; first 3 sessions; under-18; min_app_version.
- `HomeViewModelTest`: ad item last (before footer), emitted only after `AdSlotNearViewport`, removed on entitlement flip, `NoFill` → no item.
- Roborazzi: `NativeAdSlot` Loading/Loaded (fake view) at font scale 1.0/2.0, light/dark.
- `FakeNativeAdLoader` in `:core:testing`; real SDK only in a manual QA build with test ad unit ids (`ca-app-pub-3940256099942544/…` test units in debug).

Analytics (consent-gated): `ad_slot_view{screen}`, `ad_loaded`, `ad_no_fill`, `ad_click`, `ad_remove_tap`.
