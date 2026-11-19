import UIKit
import UserNotifications
import ComposeApp

/// Starts Kotlin (Koin) at launch — also for background launches with no UI (notification actions, background
/// pushes, BGTasks; platform-interop.md §2a) — and forwards OS callbacks to the shared code.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    /// Swift implementations of the Kotlin bridge protocols. A nil bridge makes Kotlin use its stub
    /// (free plan, guest only, no push, no ads) until the SDK is added with Swift Package Manager.
    static let bridges = IosPlatformBridges(
        billing: StoreKitBillingBridge(),
        auth: AuthBridge(),
        push: nil, // FE-08: FcmPushBridge (Firebase Messaging via SPM, APNs token → Messaging)
        ads: nil   // FE-27/FE-76: AdMobNativeAdLoader + NativeAdViewFactory (Google Mobile Ads + UMP via SPM)
    )

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let info = Bundle.main.infoDictionary ?? [:]
        #if DEBUG
        let debug = true
        #else
        let debug = false
        #endif
        KoinIosKt.startSmIfNeeded(
            bridges: AppDelegate.bridges,
            apiBaseUrl: info["SMApiBaseUrl"] as? String ?? "https://api.sarkarimatch.in/",
            versionName: info["CFBundleShortVersionString"] as? String ?? "0.1.0",
            versionCode: Int32(info["CFBundleVersion"] as? String ?? "1") ?? 1,
            debug: debug
        )
        UNUserNotificationCenter.current().delegate = self
        application.registerForRemoteNotifications()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // FE-08: Messaging.messaging().apnsToken = deviceToken; the FCM token then reaches Kotlin via
        // IosPushEvents.shared.onNewToken(token:).
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        IosPushEvents.shared.onMessage(data: userInfo)
        completionHandler(.newData)
    }

    // Show reminders while the app is in the foreground too.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        // FE-14/FE-70: actions (भर दिया / बाद में) and the `deepLink` in userInfo go to the shared
        // notification handler and ExternalUriHandler.
        completionHandler()
    }
}
