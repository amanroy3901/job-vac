package com.sarkarimatch.core.auth

/**
 * Supplies the bearer token for account calls (`/v1/session` and the `/v1/me/` calls). Production: the Firebase ID token
 * (anonymous / Google / Apple; FE-06). Local development: [DevIdTokenProvider].
 */
interface IdTokenProvider {
    /** `null` = no account possible right now (guest without Firebase); account features stay off. */
    suspend fun idToken(forceRefresh: Boolean = false): String?
}

/** No Firebase SDK wired yet: guests only. */
class NoIdTokenProvider : IdTokenProvider {
    override suspend fun idToken(forceRefresh: Boolean): String? = null
}

/**
 * Backend dev auth (AUTH_MODE=dev): `Bearer dev:<uid>` makes the server treat the caller as an anonymous Firebase
 * user `<uid>`. Only bound when `AppConfig.devAuth` is true (Android `dev` flavour, desktop dev target).
 */
class DevIdTokenProvider(private val uid: suspend () -> String) : IdTokenProvider {
    override suspend fun idToken(forceRefresh: Boolean): String = "dev:" + uid()
}
