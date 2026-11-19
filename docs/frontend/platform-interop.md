# Platform interop: Android + iOS from one codebase

Status: design-level architecture v2 (2026-11-19, founder decision 9 in `docs/PRD.md`). This file is the single place
that says **what is shared, what is platform code, and how Kotlin reaches Swift**. Every other frontend doc refers here
for the iOS side.

## 1. What is shared and what is not

| Layer | Shared (`commonMain`) | Android (`androidMain`, Kotlin) | iOS (`iosMain` Kotlin + `iosApp` Swift) |
| --- | --- | --- | --- |
| Domain: models, `LocalizedText`, IST formatting, ids | 100 % | — | — |
| Eligibility engine (`:core:eligibility` → `:matching-core`), reminder planner (`:reminder-core`) | 100 % | — | — |
| Data: repositories, catalog sync, outbox, backup crypto, entitlement token check | ~95 % | Room/DataStore file paths, connectivity | file paths, connectivity (`NWPathMonitor` in Kotlin via `platform.Network`) |
| Database | Room KMP schema, DAOs, migrations | SQLite driver | SQLite driver |
| Network | Ktor client, DTOs, auth/App Check/headers plugins | OkHttp engine | Darwin engine |
| UI: theme, components, all screens S01–S49, sheets, dialogs, navigation | ~98 % (Compose Multiplatform) | `MainActivity`, system bars, predictive back | `ComposeUIViewController`, safe-area insets, swipe-back |
| ViewModels / UDF | 100 % (`androidx.lifecycle` KMP ViewModel) | — | — |
| Notifications: what to show, when, dedupe, caps, quiet hours (`DeliveryGate`, `NotificationComposer` text) | 100 % | channels, `NotificationCompat`, `BroadcastReceiver`, `AlarmManager`, WorkManager | `UNUserNotificationCenter` (callable from Kotlin), categories, `BGTaskScheduler` |
| Push | `PushHandler` | `FirebaseMessagingService` | FCM iOS SDK + APNs in Swift → `PushHandler` |
| Auth | `AuthRepository` / `SessionRepository` | Credential Manager + Firebase Auth (Kotlin) | Google Sign-In iOS + Sign in with Apple + Firebase Auth (Swift) |
| Billing | `EntitlementRepository`, paywall | Play Billing (Kotlin) | StoreKit 2 (Swift) |
| Ads | `AdsGate`, slot state | GMA Next-Gen + UMP (Kotlin) | Google Mobile Ads iOS + UMP (Swift) |
| Backup key | AES-GCM in common (`cryptography-kotlin` or platform `expect`) | Block Store | iCloud Keychain (`kSecAttrSynchronizable`) |

Rule of thumb: **a screen, a rule, a string or a schedule is written once.** Only an OS service, an SDK that exists
only as Swift/ObjC, or a lifecycle hook is platform code.

## 2. `expect`/`actual` vs interfaces + DI (Koin)

| Use | When | Examples |
| --- | --- | --- |
| **Interface in `commonMain` + implementation bound in Koin** (default) | Anything with state, an SDK, a lifecycle, or that tests must fake | `BillingClientPort`, `AuthPlatform`, `PushPlatform`, `LocalNotifier`, `BackgroundScheduler`, `NativeAdLoader`, `SecureKeyStore`, `ConnectivityObserver`, `Clock` |
| **`expect`/`actual` function or class** | Small, stateless, never faked, no SDK | `expect fun platformName(): String`, `expect val appVersionCode: Int`, `expect fun openUrl(url)` wrappers, `expect class AtomicCounter`, Room `getDatabaseBuilder()`, DataStore file path |
| **Swift implementation of a Kotlin interface, injected at start** | The SDK exists only for Swift/ObjC (or is far easier there) | StoreKit 2, Sign in with Apple, Google Sign-In iOS, FCM iOS + APNs, Google Mobile Ads iOS + UMP, Firebase Auth/App Check/Crashlytics iOS |

Why not `expect`/`actual` for everything: `expect class` cannot be replaced in tests, forces every platform to share one
constructor shape, and cannot be implemented in Swift. Interfaces keep the `commonTest` suite (which is most of the
tests) on fakes, exactly as the Android-only design did with Hilt.

**DI: Koin** (replaces Hilt). Hilt is Android-only (annotation processing on the JVM, no Kotlin/Native), so a shared
graph needs a KMP DI. Koin is multiplatform, has Compose Multiplatform `koinViewModel()`, needs no code generation,
and lets Swift register implementations at startup. Alternatives considered: kotlin-inject (compile-time safety, KSP on
every target → slower iOS builds), manual DI (a solo developer would rebuild Koin badly). Compile-time safety is
recovered with a `KoinGraphTest` in `commonTest`/`androidUnitTest` that calls `koinApplication { modules(all) }.checkModules()`
(or `verify()`) with fake platform bindings.

```kotlin
// commonMain — :core:data/di
val dataModule = module {
    single<MatchActionRepository> { DefaultMatchActionRepository(get(), get(), get(), get()) }
    single { CatalogSync(get(), get(), get(), get(), get()) }
    /* … */
}
// commonMain — the ports platform code must provide
class PlatformBindings(
    val auth: AuthPlatform, val billing: BillingClientPort, val push: PushPlatform,
    val ads: NativeAdLoader, val keyStore: SecureKeyStore, val crash: CrashReporter,
)
fun platformModule(b: PlatformBindings) = module {
    single { b.auth }; single { b.billing }; single { b.push }; single { b.ads }; single { b.keyStore }; single { b.crash }
}
fun startSm(b: PlatformBindings, extra: List<Module> = emptyList()) =
    startKoin { modules(listOf(platformModule(b), dataModule, networkModule, databaseModule, notificationsModule, featureModules) + extra) }
```

Android `SmApplication.onCreate` calls `startSm(PlatformBindings(AndroidAuthPlatform(this), PlayBillingPort(this), …), listOf(androidModule))`.
iOS: `iOSApp.swift` builds Swift objects and calls the exported Kotlin `KoinIosKt.startSm(bindings:)` **before** the
first `ComposeUIViewController` and before returning from `application(_:didFinishLaunchingWithOptions:)` (a
notification action can cold-start the app in the background with no UI).

### 2a. Names as built in `mobile/` (2026-11-19)

The migration in `mobile/` uses these names; the concepts in this file map 1:1 and new code follows the built names.

| Concept in these docs | Built (`:core:data`) |
| --- | --- |
| `BillingClientPort` / `StorePort` | common `core.billing.BillingService` (`products`, `purchase(product, accountToken)`, `restore`, `plan`); iOS Swift protocol `IosBillingBridge` adapted by `IosBillingService` |
| `AuthPlatform` | common `core.auth.AuthService` (`AuthProvider.GOOGLE/APPLE`, `ProviderCredential`); iOS `IosAuthBridge` (`signInWithGoogle`, `signInWithApple(nonce)`) → `IosAuthService`; `GuestOnlyAuthService` stub |
| `PushPlatform` | common `core.push.PushService`; iOS `IosPushBridge` → `IosPushService`; `NoPushService` stub |
| `NativeAdLoader` | common `core.ads.AdsService`; iOS `IosAdsBridge` → `IosAdsService`; `NoAdsService` stub |
| `ReminderScheduler` / `ReminderWindow` | common `core.notifications.ReminderScheduler` (`maxPending`, `replaceAll`, `cancel`); `IosReminderScheduler` (64-cap) |
| `PlatformBindings` + `startSm(bindings)` | `IosPlatformBridges(billing, auth, push, ads)` passed from Swift to `MainViewController(bridges:)` → `iosDataModule(bridges)`; every bridge is nullable and falls back to a stub (free plan, guest only, no push, no ads), so the shell runs before every SDK is wired |

Bridge callbacks are concrete Kotlin classes with one method per outcome (`IosPurchaseCallback.onPurchased/onPending/
onCancelled/onError`) instead of lambdas — the same rule as §3.2, even friendlier to Swift. One gap against §2: Koin is
started from `MainViewController`, so a **background launch with no UI** (notification action, background push, BG
task) must also start Koin; FE-70 moves the start to `AppDelegate.didFinishLaunching` (an idempotent
`startSmIfNeeded(bridges)`).

## 3. Swift interop boundary

### 3.1 What must be Swift (and why)

| Area | Swift code in `iosApp/` | Why not Kotlin/Native |
| --- | --- | --- |
| **StoreKit 2** (`Product`, `purchase()`, `Transaction.updates`, `Transaction.currentEntitlements`, `AppStore.sync()`, `isEligibleForIntroOffer`) | `StoreKitBillingPort` | StoreKit 2 is Swift-only (async/await, structs); not visible to cinterop |
| **Sign in with Apple** (`ASAuthorizationController`, nonce) | `AppleSignInProvider` | ObjC API would work, but the Firebase Apple flow (`OAuthProvider.appleCredential`) is simplest in Swift |
| **Google Sign-In iOS SDK** (`GIDSignIn`) | `GoogleSignInProvider` | SPM-only Swift package |
| **Firebase iOS** (Auth, Messaging, App Check with App Attest, Crashlytics, Installations) | `FirebaseAuthPlatform`, `FcmPushPlatform`, `AppCheckTokenProvider`, `CrashlyticsReporter` | Firebase iOS is distributed through Swift Package Manager; linking it into the Kotlin framework via cinterop (what GitLive does) adds a CocoaPods/SPM-to-cinterop build step on every iOS build |
| **APNs registration + `UNUserNotificationCenterDelegate`** (foreground presentation, action responses) | `AppDelegate` | Delegates and `didRegisterForRemoteNotifications` must be in the app delegate; it forwards to Kotlin |
| **Google Mobile Ads iOS + UMP** (`NativeAdLoader`, `GADNativeAdView`, `UMPConsentInformation`) | `AdMobNativeAdLoader`, `NativeAdViewFactory` | SPM-only; the ad view must be a UIKit view registered with the SDK |
| **Keychain (synchronizable) backup key** | optional — can be Kotlin via `platform.Security` | Kotlin is fine here; Swift only if Kotlin cinterop gets awkward |
| `BGTaskScheduler` registration | `AppDelegate` registers identifiers; task body calls Kotlin | Registration must happen before launch finishes; body is shared Kotlin |

Everything else on iOS is **Kotlin in `iosMain`** using the platform bindings Kotlin/Native ships
(`platform.UserNotifications`, `platform.Foundation`, `platform.UIKit`, `platform.BackgroundTasks`, `platform.Network`):
local notification scheduling, the pending-request window, `BGTaskScheduler` task bodies, connectivity, file paths,
locale, opening URLs.

### 3.2 How Kotlin calls Swift

1. Kotlin declares a **callback-style interface** in `commonMain` (the port) — not `suspend`, because a Swift class
   implementing a Kotlin `suspend fun` gets an awkward completion-handler signature and no structured cancellation.
2. Kotlin wraps each port in a coroutine-friendly adapter (`suspendCancellableCoroutine`, `callbackFlow`) in common
   code; repositories only see the adapter.
3. Swift implements the interface (it is exported in the `ComposeApp` framework as an ObjC protocol) and passes an
   instance to `startSm(bindings:)`.
4. Swift → Kotlin calls (notification response, push received, URL opened, background task fired) go through small
   exported Kotlin entry points (`IosEntryPoints.onNotificationResponse(...)`, `ExternalUriHandler.onNewUri(...)`).

```kotlin
// commonMain — the port
interface StorePort {
    fun loadProducts(ids: List<String>, cb: (List<StoreProduct>?, StoreError?) -> Unit)
    fun purchase(productId: String, appAccountToken: String, cb: (StorePurchaseResult) -> Unit)
    fun currentEntitlements(cb: (List<StoreTransaction>) -> Unit)
    fun sync(cb: (StoreError?) -> Unit)                       // AppStore.sync() — user-initiated restore only
    fun setTransactionListener(listener: (StoreTransaction) -> Unit)
    fun finish(transactionId: String)
}
// commonMain — adapter
suspend fun StorePort.purchaseAwait(id: String, token: String) = suspendCancellableCoroutine { c -> purchase(id, token) { c.resume(it) } }
```

```swift
// iosApp — the Swift implementation
final class StoreKitBillingPort: NSObject, StorePort {
    func purchase(productId: String, appAccountToken: String, cb: @escaping (StorePurchaseResult) -> Void) {
        Task { @MainActor in
            guard let p = try? await Product.products(for: [productId]).first,
                  let uuid = UUID(uuidString: appAccountToken) else { return cb(.failed("product")) }
            switch try? await p.purchase(options: [.appAccountToken(uuid)]) {
            case .success(let v): cb(.purchased(jws: v.jwsRepresentation, transactionId: String(v.unsafePayloadValue.id)))
            case .pending: cb(.pending)
            case .userCancelled, .none: cb(.cancelled)
            @unknown default: cb(.cancelled)
            }
        }
    }
    /* … */
}
```

Interop rules:

- **Data across the boundary is flat**: `String`, `Long`, `Boolean`, small Kotlin data classes, `List` of them. No
  Kotlin `sealed` hierarchies in Swift-facing signatures (they export as class trees Swift cannot `switch` on
  exhaustively); use a result class with a `kind` enum. No `Flow` in Swift-facing APIs.
- **Threading:** Swift calls back on any thread; adapters hop to `Dispatchers.Main` only for UI. Kotlin/Native's memory
  model allows sharing objects across threads (no freezing).
- **No SKIE / Swift Export at first.** Both make Swift-facing Kotlin nicer (exhaustive sealed types, `async` flows) but
  add a compiler plugin or an experimental export to a solo developer's build. The callback ports above are ~10
  interfaces; revisit if the Swift side grows.
- **Framework**: one static framework `ComposeApp` (all KMP modules exported through `composeApp`), linked by the Xcode
  project through the Gradle `embedAndSignAppleFrameworkForXcode` build phase. Swift packages (Firebase, GoogleSignIn,
  GoogleMobileAds, UMP) are added to the Xcode project with SPM, never to Gradle.

### 3.3 iOS app shell (`iosApp/`)

```
iosApp/
├─ iosApp/
│  ├─ iOSApp.swift              @main SwiftUI App; UIApplicationDelegateAdaptor(AppDelegate)
│  ├─ AppDelegate.swift         FirebaseApp.configure, startSm(bindings), UNUserNotificationCenter delegate,
│  │                            APNs token → Messaging, BGTaskScheduler.register, universal links → ExternalUriHandler
│  ├─ ContentView.swift         ComposeView: UIViewControllerRepresentable → MainViewControllerKt.MainViewController()
│  ├─ Bridges/                  StoreKitBillingPort, FirebaseAuthPlatform (+ GoogleSignInProvider, AppleSignInProvider),
│  │                            FcmPushPlatform, AdMobNativeAdLoader + NativeAdViewFactory, CrashlyticsReporter, AppCheckTokenProvider
│  ├─ Info.plist                CFBundleLocalizations (en, hi), BGTaskSchedulerPermittedIdentifiers, UIBackgroundModes
│  │                            (remote-notification, fetch, processing), GADApplicationIdentifier, SKAdNetworkItems
│  ├─ iosApp.entitlements       aps-environment, associated domains (applinks:sarkarimatch.in), Sign in with Apple,
│  │                            time-sensitive notifications, App Group group.in.sarkarimatch.app (DB + NSE hints),
│  │                            keychain access group (backup key)
│  ├─ PrivacyInfo.xcprivacy     our required-reason APIs (UserDefaults, file timestamps — Kotlin/Native runtime: verify)
│  └─ Localizable.xcstrings     generated at build time from composeResources: notification templates (loc-keys) only
└─ NotificationService/         (P1, see notifications.md §11) Notification Service Extension
```

## 4. App Store review risks (and how the design answers them)

Guideline text checked on developer.apple.com/app-store/review/guidelines (page last updated 16 Oct 2026).

| Guideline | Risk for Sarkari Match | Answer |
| --- | --- | --- |
| **4.8 Login Services** — apps that use a third-party login (Google Sign-In named) for the user's primary account "must also offer as an equivalent option another login service" that limits data to name and email, lets users keep their email private, and does not collect interactions for advertising without consent | Google Sign-In creates the account that holds Pro and the backup | **Sign in with Apple** on iOS, shown next to Google with equal prominence (O11/B16/S03/S10/S26/S38). It meets all three criteria; guest stays available. Firebase Auth Apple provider; Hide-My-Email relay addresses are fine because we store no email beyond display |
| **5.1.1(v) Account sign-in / deletion** — let people use the app without a login if there are no significant account features; if the app supports account creation, offer account deletion in the app | — | Guest by default (already); D03 deletes the account in-app (`deleteAccount`), and for Apple-linked accounts the **server revokes the Apple token** (from `apple_authorization_code` sent in `startSession`; fresh code requested when `apple_revocation = token_missing`) |
| **4.2 Minimum functionality** — "elevate it beyond a repackaged website" | A job-alert list can look like a website | Native local matching, local reminders with actions, offline, a full native CMP UI (no WebView). Review notes explain on-device matching and give a test profile that yields matches |
| **5.2.1 / 2.3.1 / 1.1.6** — no misleading representations, accurate metadata, no false information | A "sarkari" app can be read as a government app; Apple has rejected apps that present official information without being the official source | Name/subtitle never say "official"; no emblem/tricolour (same as Play impersonation policy); App Store description and S03/S21/S39 carry "Sarkari Match एक निजी ऐप है, कोई सरकारी ऐप नहीं" / "Not a government app. Not affiliated with any government body"; every match links its official PDF/source (PRD NFR). Review notes state the sources (SSC, RRB, IBPS, UPSC, state boards) and that content is human-reviewed |
| **3.1.1 In-app purchase** — features unlocked in the app must use IAP; **3.1.2(a)** auto-renewables may offer a free trial configured in App Store Connect | A server-granted, no-payment 14-day trial unlocks paid features without IAP | On iOS the trial is a **StoreKit introductory offer** (free 2 weeks on the yearly subscription); see billing.md §10. Ad removal is part of Pro, bought through IAP |
| **3.1.3(b) Multiplatform services** — users may access subscriptions acquired on other platforms "provided those items are also available as in-app purchases within the app" | Pro bought on Android, used on iOS (and the reverse) | Allowed: both Pro products exist as IAP on iOS; entitlement is server-side per account |
| **3.1.2(c)** describe what the user gets before asking to subscribe; Schedule 2 disclosures | Paywall copy | S30 on iOS shows price, period, auto-renew terms, trial terms, links to Terms/Privacy, "Restore purchases" |
| **4.5.4 Push notifications** — must not be required for the app to function; no marketing without opt-in | Our product *is* reminders | The app works without push (feed, local reminders); no promotional pushes ever (trial-ending N-31/N-33 are account/service messages; keep them informational) |
| Data use: App Privacy labels, privacy manifest, ATT | Ads SDK | Non-personalised ads, **no ATT prompt** (ads.md §7); privacy labels list SDK identifiers/diagnostics; our `PrivacyInfo.xcprivacy` + SDK manifests |
| Age rating | 17-year-old users; ads | Rate per the current questionnaire (no mature content; ads present). Minor mode still applies (no ads/account under 18) |
