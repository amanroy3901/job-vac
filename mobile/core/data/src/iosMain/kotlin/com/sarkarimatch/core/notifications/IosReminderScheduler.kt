package com.sarkarimatch.core.notifications

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.time.Clock

/**
 * Local reminders on iOS with UNUserNotificationCenter. iOS keeps at most 64 pending requests per app and silently
 * drops the rest, so [replaceAll] keeps only the 64 soonest; the planner re-plans whenever the app runs, on a
 * background refresh and on every push (FE-14), so later reminders get scheduled as earlier ones fire. Critical
 * alerts (bypassing Focus) need an Apple entitlement and are not used; `critical` maps to time-sensitive in FE-14.
 */
class IosReminderScheduler : ReminderScheduler {
    override val maxPending: Int = IOS_MAX_PENDING

    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ -> cont.resume(granted) }
    }

    override suspend fun replaceAll(reminders: List<LocalReminder>) {
        center.removeAllPendingNotificationRequests()
        val now = Clock.System.now()
        reminders.filter { it.at > now }.soonest(maxPending).forEach { r ->
            val content = UNMutableNotificationContent()
            content.title = r.title
            content.body = r.body
            content.threadIdentifier = r.channel.id
            content.sound = UNNotificationSound.defaultSound
            content.userInfo = buildMap<Any?, Any?> {
                put("channel", r.channel.id)
                r.deepLink?.let { put("deepLink", it) }
            }
            val seconds = (r.at - now).inWholeSeconds.coerceAtLeast(1).toDouble()
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier(r.id, content, trigger)
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
    }

    override suspend fun cancel(ids: List<String>) {
        center.removePendingNotificationRequestsWithIdentifiers(ids)
    }

    override suspend fun cancelAll() {
        center.removeAllPendingNotificationRequests()
    }

    private companion object {
        const val IOS_MAX_PENDING = 64
    }
}
