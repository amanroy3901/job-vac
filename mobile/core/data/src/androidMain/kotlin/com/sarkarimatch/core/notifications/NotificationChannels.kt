package com.sarkarimatch.core.notifications

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The canonical channel set (notifications.md §2, design 05 §1). IDs are permanent once shipped. Channels are
 * (re)created on every app start and on language switch with the same IDs, so names follow the app language.
 * `family` is created only when this phone accepts a family invite ([ensureFamily]).
 */
@Singleton
class NotificationChannels @Inject constructor() {

    enum class Channel(
        val id: String,
        @StringRes val nameRes: Int,
        val importance: Int,
        val vibrationPattern: LongArray? = null,
    ) {
        CRITICAL("critical", R.string.channel_critical, NotificationManagerCompat.IMPORTANCE_HIGH, longArrayOf(0, 250, 150, 250)),
        NEW_MATCH("new_match", R.string.channel_new_match, NotificationManagerCompat.IMPORTANCE_HIGH),
        REMINDERS("reminders", R.string.channel_reminders, NotificationManagerCompat.IMPORTANCE_DEFAULT),
        JOB_UPDATES("job_updates", R.string.channel_job_updates, NotificationManagerCompat.IMPORTANCE_DEFAULT),
        DIGEST("digest", R.string.channel_digest, NotificationManagerCompat.IMPORTANCE_LOW),
        ACCOUNT("account", R.string.channel_account, NotificationManagerCompat.IMPORTANCE_LOW),
        FAMILY("family", R.string.channel_family, NotificationManagerCompat.IMPORTANCE_HIGH),
        ;

        companion object {
            /** Unknown channel in a push → reminders. */
            fun fromId(id: String?): Channel = entries.firstOrNull { it.id == id } ?: REMINDERS
        }
    }

    /** Idempotent; pass a context whose resources are in the app language (an Activity on API < 33). */
    fun ensureCreated(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val channels = Channel.entries.filter { it != Channel.FAMILY } +
            listOfNotNull(Channel.FAMILY.takeIf { manager.getNotificationChannelCompat(it.id) != null })
        manager.createNotificationChannelsCompat(channels.map { build(context, it) })
    }

    fun ensureFamily(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(build(context, Channel.FAMILY))
    }

    private fun build(context: Context, channel: Channel): NotificationChannelCompat =
        NotificationChannelCompat.Builder(channel.id, channel.importance)
            .setName(context.getString(channel.nameRes))
            .apply {
                channel.vibrationPattern?.let { setVibrationEnabled(true).setVibrationPattern(it) }
                if (channel == Channel.DIGEST) setSound(null, null)
            }
            .build()
}
