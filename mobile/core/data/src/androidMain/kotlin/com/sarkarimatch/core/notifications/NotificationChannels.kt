package com.sarkarimatch.core.notifications

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.sarkarimatch.core.data.R

/**
 * The canonical channel set (notifications.md §2, design 05 §1). IDs are permanent once shipped ([SmChannel]).
 * Channels are (re)created on every app start and on language switch with the same IDs, so names follow the app
 * language. `family` is created only when this phone accepts a family invite ([ensureFamily]).
 */
class NotificationChannels {

    private class Spec(@StringRes val nameRes: Int, val importance: Int, val vibrationPattern: LongArray? = null)

    private fun spec(channel: SmChannel): Spec = when (channel) {
        SmChannel.CRITICAL ->
            Spec(R.string.channel_critical, NotificationManagerCompat.IMPORTANCE_HIGH, longArrayOf(0, 250, 150, 250))
        SmChannel.NEW_MATCH -> Spec(R.string.channel_new_match, NotificationManagerCompat.IMPORTANCE_HIGH)
        SmChannel.REMINDERS -> Spec(R.string.channel_reminders, NotificationManagerCompat.IMPORTANCE_DEFAULT)
        SmChannel.JOB_UPDATES -> Spec(R.string.channel_job_updates, NotificationManagerCompat.IMPORTANCE_DEFAULT)
        SmChannel.DIGEST -> Spec(R.string.channel_digest, NotificationManagerCompat.IMPORTANCE_LOW)
        SmChannel.ACCOUNT -> Spec(R.string.channel_account, NotificationManagerCompat.IMPORTANCE_LOW)
        SmChannel.FAMILY -> Spec(R.string.channel_family, NotificationManagerCompat.IMPORTANCE_HIGH)
    }

    /** Idempotent; pass a context whose resources are in the app language (an Activity on API < 33). */
    fun ensureCreated(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val channels = SmChannel.entries.filter { it != SmChannel.FAMILY } +
            listOfNotNull(SmChannel.FAMILY.takeIf { manager.getNotificationChannelCompat(it.id) != null })
        manager.createNotificationChannelsCompat(channels.map { build(context, it) })
    }

    fun ensureFamily(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(build(context, SmChannel.FAMILY))
    }

    private fun build(context: Context, channel: SmChannel): NotificationChannelCompat {
        val spec = spec(channel)
        return NotificationChannelCompat.Builder(channel.id, spec.importance)
            .setName(context.getString(spec.nameRes))
            .apply {
                spec.vibrationPattern?.let { setVibrationEnabled(true).setVibrationPattern(it) }
                if (channel == SmChannel.DIGEST) setSound(null, null)
            }
            .build()
    }
}
