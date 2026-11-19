import SwiftUI
import UIKit
import ComposeApp

/// Hosts the shared Compose UI (`MainViewController.kt` in :composeApp iosMain).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose handles insets itself (safeDrawingPadding / Scaffold), so draw edge to edge.
        ComposeView()
            .ignoresSafeArea()
    }
}
