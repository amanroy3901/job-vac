import SwiftUI

/// SwiftUI entry point. All UI is Compose Multiplatform (ComposeApp framework); AppDelegate starts the shared Kotlin
/// graph before any scene exists, so background launches work too.
@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
