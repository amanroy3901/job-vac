package com.sarkarimatch.core.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.sarkarimatch.core.platform.ActivityProvider

/**
 * Sign in with Google through Credential Manager (founder decision 2). [serverClientId] is the backend's OAuth web
 * client id; while it is blank (not configured yet) only guest mode is offered.
 */
class CredentialManagerAuthService(
    private val context: Context,
    private val activities: ActivityProvider,
    private val serverClientId: String?,
) : AuthService {
    override val providers: List<AuthProvider> =
        if (serverClientId.isNullOrBlank()) emptyList() else listOf(AuthProvider.GOOGLE)

    override suspend fun signIn(provider: AuthProvider, nonce: String?): AuthResult {
        if (provider != AuthProvider.GOOGLE || serverClientId.isNullOrBlank()) {
            return AuthResult.Failed("$provider sign-in is not available on Android")
        }
        val activity = activities.current ?: return AuthResult.Failed("no foreground activity")
        val option = GetSignInWithGoogleOption.Builder(serverClientId)
            .apply { if (nonce != null) setNonce(nonce) }
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                AuthResult.Success(
                    ProviderCredential(AuthProvider.GOOGLE, google.idToken, nonce, google.displayName, google.email),
                )
            } else {
                AuthResult.Failed("unexpected credential type ${credential.type}")
            }
        } catch (_: GetCredentialCancellationException) {
            AuthResult.Cancelled
        } catch (_: NoCredentialException) {
            AuthResult.NoAccount
        } catch (e: GetCredentialException) {
            AuthResult.Failed(e.message ?: e.type)
        }
    }

    override suspend fun signOut() {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
}
