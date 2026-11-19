package com.sarkarimatch.core.push

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** One data message from the backend (notifications.md §3): `type`, ids and channel; text is composed on device. */
data class PushMessage(val data: Map<String, String>)

/**
 * Remote push. Android: Firebase Cloud Messaging. iOS: Firebase Messaging iOS SDK on top of APNs (Swift side),
 * so the backend sends through FCM to both. No WhatsApp / SMS (founder decision 7).
 */
interface PushService {
    /** Current registration token, or null when push is unavailable (no Firebase config, no Play services). */
    suspend fun token(): String?

    /** Tokens as they rotate; each is registered with POST /v1/devices (FE-08). */
    val tokenUpdates: Flow<String>

    /** Data messages received while the app process is alive. */
    val messages: Flow<PushMessage>
}

class NoPushService : PushService {
    override suspend fun token(): String? = null
    override val tokenUpdates: Flow<String> = emptyFlow()
    override val messages: Flow<PushMessage> = emptyFlow()
}
