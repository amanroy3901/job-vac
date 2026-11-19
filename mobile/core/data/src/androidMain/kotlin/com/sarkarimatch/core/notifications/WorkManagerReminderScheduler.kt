package com.sarkarimatch.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * On-device backup reminders on Android via WorkManager (survives reboots and Doze, fires without network).
 * `critical` last-day reminders move to exact alarms (AlarmManager + S14 permission) with FE-14.
 */
class WorkManagerReminderScheduler(
    private val context: Context,
    private val channels: NotificationChannels,
) : ReminderScheduler {
    override val maxPending: Int? = null

    override suspend fun requestPermission(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override suspend fun replaceAll(reminders: List<LocalReminder>) {
        val work = WorkManager.getInstance(context)
        work.cancelAllWorkByTag(TAG)
        channels.ensureCreated(context)
        val now = Clock.System.now()
        reminders.filter { it.at > now }.forEach { r ->
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay((r.at - now).inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_ID to r.id,
                        KEY_TITLE to r.title,
                        KEY_BODY to r.body,
                        KEY_CHANNEL to r.channel.id,
                        KEY_LINK to r.deepLink,
                    ),
                )
                .addTag(TAG)
                .build()
            work.enqueueUniqueWork(uniqueName(r.id), ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun cancel(ids: List<String>) {
        val work = WorkManager.getInstance(context)
        ids.forEach { work.cancelUniqueWork(uniqueName(it)) }
    }

    override suspend fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    internal companion object {
        const val TAG = "sm_reminder"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_CHANNEL = "channel"
        const val KEY_LINK = "link"

        fun uniqueName(id: String) = "reminder_$id"
    }
}

/** Posts one reminder notification; tapping it opens the deep link in MainActivity. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
        if (!granted) return Result.success()
        val data = inputData
        val id = data.getString(WorkManagerReminderScheduler.KEY_ID) ?: return Result.failure()
        val link = data.getString(WorkManagerReminderScheduler.KEY_LINK)
        val intent = (
            link?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) } ?: ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        )
            ?.setPackage(ctx.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = intent?.let {
            PendingIntent.getActivity(ctx, id.hashCode(), it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(ctx, SmChannel.fromId(data.getString(WorkManagerReminderScheduler.KEY_CHANNEL)).id)
            .setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle(data.getString(WorkManagerReminderScheduler.KEY_TITLE))
            .setContentText(data.getString(WorkManagerReminderScheduler.KEY_BODY))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(ctx).notify(id, 0, notification)
        return Result.success()
    }
}
