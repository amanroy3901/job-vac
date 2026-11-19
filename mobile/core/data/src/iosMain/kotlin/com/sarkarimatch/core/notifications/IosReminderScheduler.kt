package com.sarkarimatch.core.notifications

import com.sarkarimatch.core.model.Ist
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithName
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.time.Clock

/**
 * Local reminders on iOS with UNUserNotificationCenter (notifications.md §5.2). iOS keeps at most 64 pending
 * requests per app and silently drops the rest, so the window holds the [WINDOW] (60) soonest and leaves
 * [RESERVED] (4) for immediate notifications (new match, action confirmations, self-test). Applied as a diff against
 * the pending requests (never remove-all, which briefly leaves nothing scheduled), with `UNCalendarNotificationTrigger`
 * in Asia/Kolkata — deadlines are IST wall-clock times, and a time-interval trigger would drift when the user changes
 * the clock or time zone. The planner refills the window on foreground, Room changes, pushes and BGAppRefreshTask
 * (FE-72: R-last/R1 priority, sentinel request, categories and interruption levels).
 */
class IosReminderScheduler : ReminderScheduler {
    override val maxPending: Int = WINDOW

    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ -> cont.resume(granted) }
    }

    override suspend fun replaceAll(reminders: List<LocalReminder>) {
        val now = Clock.System.now()
        val wanted = reminders.filter { it.at > now }.soonest(maxPending).associateBy { it.id }
        val pending = pendingIds().filter { it.startsWith(ID_PREFIX) }.toSet()
        val obsolete = pending - wanted.keys.map(::requestId).toSet()
        if (obsolete.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(obsolete.toList())
        // Same identifier = replace, so re-adding an existing reminder is idempotent and picks up new text.
        wanted.values.forEach { add(it) }
    }

    override suspend fun cancel(ids: List<String>) {
        center.removePendingNotificationRequestsWithIdentifiers(ids.map(::requestId))
    }

    override suspend fun cancelAll() {
        center.removePendingNotificationRequestsWithIdentifiers(pendingIds().filter { it.startsWith(ID_PREFIX) })
    }

    private suspend fun pendingIds(): List<String> = suspendCancellableCoroutine { cont ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            cont.resume(requests.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier })
        }
    }

    private fun add(r: LocalReminder) {
        val content = UNMutableNotificationContent()
        content.setTitle(r.title)
        content.setBody(r.body)
        content.setThreadIdentifier(r.channel.id)
        content.setSound(UNNotificationSound.defaultSound)
        content.setUserInfo(buildMap<Any?, Any?> {
            put("channel", r.channel.id)
            r.deepLink?.let { put("deepLink", it) }
        })
        val ist = r.at.toLocalDateTime(Ist.ZONE)
        val components = NSDateComponents()
        components.timeZone = NSTimeZone.timeZoneWithName(IST_ZONE)
        components.year = ist.year.toLong()
        components.month = ist.month.ordinal.toLong() + 1
        components.day = ist.day.toLong()
        components.hour = ist.hour.toLong()
        components.minute = ist.minute.toLong()
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(requestId(r.id), content, trigger)
        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    private fun requestId(reminderId: String) = ID_PREFIX + reminderId

    private companion object {
        const val IOS_MAX_PENDING = 64
        const val RESERVED = 4
        const val WINDOW = IOS_MAX_PENDING - RESERVED
        const val ID_PREFIX = "r:"
        const val IST_ZONE = "Asia/Kolkata"
    }
}
