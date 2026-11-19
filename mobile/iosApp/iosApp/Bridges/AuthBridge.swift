import AuthenticationServices
import CryptoKit
import UIKit
import ComposeApp

/// Kotlin `IosAuthBridge`: Sign in with Apple (AuthenticationServices, implemented) and Google Sign-In for iOS
/// (FE-06: add the GoogleSignIn package with SPM, GIDClientID + URL scheme in Info.plist, then implement
/// `signInWithGoogle` with GIDSignIn and return true from `isGoogleConfigured`). App Store guideline 4.8: Apple is
/// always offered when Google is.
final class AuthBridge: NSObject, IosAuthBridge, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    private var pending: IosAuthCallback?

    func isGoogleConfigured() -> Bool { false }

    func signInWithGoogle(nonce: String?, callback: IosAuthCallback) {
        callback.onError(message: "Google Sign-In for iOS is not configured yet")
    }

    func signInWithApple(nonce: String?, callback: IosAuthCallback) {
        pending = callback
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        if let nonce {
            request.nonce = SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
        }
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func signOut() {
        // Apple has no client-side sign-out; the server revokes tokens on account deletion.
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        defer { pending = nil }
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else {
            pending?.onError(message: "no identity token")
            return
        }
        let name = credential.fullName
            .map { PersonNameComponentsFormatter().string(from: $0) }
            .flatMap { $0.isEmpty ? nil : $0 }
        pending?.onSuccess(idToken: token, displayName: name, email: credential.email)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        defer { pending = nil }
        if (error as? ASAuthorizationError)?.code == .canceled {
            pending?.onCancelled()
        } else {
            pending?.onError(message: error.localizedDescription)
        }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}
