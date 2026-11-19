package com.sarkarimatch.core.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Bridge from the FirebaseMessagingService (created by the system) to the app's PushService. */
internal object PushEvents {
    val tokens = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = MutableSharedFlow<PushMessage>(extraBufferCapacity = 32)
}

/**
 * FCM on Android. Firebase is configured by `google-services.json` + the Google Services plugin (FE-08); until then
 * no FirebaseApp exists and [token] returns null instead of crashing.
 */
class FcmPushService(private val context: Context) : PushService {
    @Suppress("DEPRECATION") // FCM 25 deprecates the token API in favour of a newer registration flow; migrate with FE-08
    override suspend fun token(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                cont.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }

    override val tokenUpdates: Flow<String> = PushEvents.tokens.asSharedFlow()
    override val messages: Flow<PushMessage> = PushEvents.messages.asSharedFlow()
}

/** Declared in this module's manifest. Text is composed on device from the data payload (notifications.md §3). */
class SmMessagingService : FirebaseMessagingService() {
    @Deprecated("Mirrors FirebaseMessagingService.onNewToken (deprecated in FCM 25); migrate with FE-08")
    override fun onNewToken(token: String) {
        PushEvents.tokens.tryEmit(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushEvents.messages.tryEmit(PushMessage(message.data))
    }
}
