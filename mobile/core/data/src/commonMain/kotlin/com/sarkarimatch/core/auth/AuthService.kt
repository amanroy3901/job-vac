package com.sarkarimatch.core.auth

/** Sign-in options (founder decisions 2 and 9): Google everywhere, Apple on iOS only, or guest (no call at all). */
enum class AuthProvider { GOOGLE, APPLE }

/** What the backend needs to create a session: the provider's ID token (verified server-side). */
data class ProviderCredential(
    val provider: AuthProvider,
    val idToken: String,
    /** Apple only: the nonce used for the request (hashed in the token); Google: null. */
    val rawNonce: String? = null,
    val displayName: String? = null,
    val email: String? = null,
)

sealed interface AuthResult {
    data class Success(val credential: ProviderCredential) : AuthResult
    data object Cancelled : AuthResult
    data object NoAccount : AuthResult
    data class Failed(val message: String) : AuthResult
}

/**
 * Platform sign-in. Android: Credential Manager + Sign in with Google. iOS: Google Sign-In for iOS and Sign in with
 * Apple (App Store guideline 4.8 requires Apple whenever a third-party login is offered), implemented in Swift.
 * The ID token is exchanged for our session by the auth repository (POST /v1/auth/…, FE-06).
 */
interface AuthService {
    /** Providers to show on this platform, in display order. */
    val providers: List<AuthProvider>

    suspend fun signIn(provider: AuthProvider, nonce: String? = null): AuthResult

    /** Clears the provider-side state (Credential Manager `clearCredentialState`, GIDSignIn.signOut). */
    suspend fun signOut()
}

/** Guest-only build (no providers). */
class GuestOnlyAuthService : AuthService {
    override val providers: List<AuthProvider> = emptyList()
    override suspend fun signIn(provider: AuthProvider, nonce: String?): AuthResult = AuthResult.Failed("sign-in not configured")
    override suspend fun signOut() = Unit
}
