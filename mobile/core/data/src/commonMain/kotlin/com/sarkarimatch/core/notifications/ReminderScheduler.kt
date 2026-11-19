package com.sarkarimatch.core.notifications

import kotlin.time.Instant

/** Notification channel ids (notifications.md §2); Android channels, iOS `threadIdentifier` / category. */
enum class SmChannel(val id: String) {
    CRITICAL("critical"),
    NEW_MATCH("new_match"),
    REMINDERS("reminders"),
    JOB_UPDATES("job_updates"),
    DIGEST("digest"),
    ACCOUNT("account"),
    FAMILY("family"),
    ;

    companion object {
        /** Unknown channel in a push → reminders. */
        fun fromId(id: String?): SmChannel = entries.firstOrNull { it.id == id } ?: REMINDERS
    }
}

/**
 * An on-device backup reminder (founder decision 7: FCM push + on-device backup reminders), already rendered in the
 * app language. [deepLink] uses the sarkarimatch:// scheme (navigation.md §5).
 */
data class LocalReminder(
    val id: String,
    val at: Instant,
    val title: String,
    val body: String,
    val channel: SmChannel,
    val deepLink: String?,
)

/**
 * Schedules local reminders that fire without network. Android: WorkManager (exact alarms for `critical` are FE-14).
 * iOS: UNUserNotificationCenter, which keeps at most [maxPending] (64) pending requests per app — the scheduler keeps
 * only the soonest ones and the rest are re-planned whenever the app runs or a push arrives (ReminderPlanner, FE-14).
 */
interface ReminderScheduler {
    /** Platform cap on pending local notifications, or null when there is none (Android). */
    val maxPending: Int?

    /** Asks for / reports the notification permission (Android 13+ POST_NOTIFICATIONS is requested by S13's UI). */
    suspend fun requestPermission(): Boolean

    /**
     * Replaces the whole set of pending reminders with [reminders] (the planner always computes the full plan).
     * Past reminders are dropped; on iOS only the [maxPending] soonest are kept.
     */
    suspend fun replaceAll(reminders: List<LocalReminder>)

    suspend fun cancel(ids: List<String>)

    suspend fun cancelAll()
}

/** Keeps the [limit] soonest reminders (iOS 64-pending cap). */
fun List<LocalReminder>.soonest(limit: Int?): List<LocalReminder> =
    if (limit == null) this else sortedBy { it.at }.take(limit)
