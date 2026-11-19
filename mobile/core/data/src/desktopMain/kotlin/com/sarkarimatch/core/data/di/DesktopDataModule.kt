package com.sarkarimatch.core.data.di

import androidx.room.Room
import com.sarkarimatch.core.ads.AdsService
import com.sarkarimatch.core.ads.NoAdsService
import com.sarkarimatch.core.auth.AuthService
import com.sarkarimatch.core.auth.GuestOnlyAuthService
import com.sarkarimatch.core.billing.BillingService
import com.sarkarimatch.core.billing.StubBillingService
import com.sarkarimatch.core.data.prefs.APP_PREFS_FILE
import com.sarkarimatch.core.data.prefs.LanguageController
import com.sarkarimatch.core.data.prefs.PrefsLanguageController
import com.sarkarimatch.core.data.prefs.createAppPrefsDataStore
import com.sarkarimatch.core.database.DATABASE_NAME
import com.sarkarimatch.core.database.SmDatabase
import com.sarkarimatch.core.database.buildSmDatabase
import com.sarkarimatch.core.notifications.LocalReminder
import com.sarkarimatch.core.notifications.ReminderScheduler
import com.sarkarimatch.core.push.NoPushService
import com.sarkarimatch.core.push.PushService
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop dev target (never shipped): OkHttp engine, Room + DataStore files in [storageDir], stub platform
 * services (guest sign-in, no billing/push/ads, reminders ignored).
 */
fun desktopDataModule(storageDir: File): Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single {
        storageDir.mkdirs()
        buildSmDatabase(Room.databaseBuilder<SmDatabase>(name = File(storageDir, DATABASE_NAME).absolutePath))
    }
    single { createAppPrefsDataStore(File(storageDir, APP_PREFS_FILE).absolutePath) }
    single<LanguageController> { PrefsLanguageController(get()) }
    single<BillingService> { StubBillingService() }
    single<AuthService> { GuestOnlyAuthService() }
    single<PushService> { NoPushService() }
    single<ReminderScheduler> { NoopReminderScheduler() }
    single<AdsService> { NoAdsService() }
}

private class NoopReminderScheduler : ReminderScheduler {
    override val maxPending: Int? = null
    override suspend fun requestPermission(): Boolean = false
    override suspend fun replaceAll(reminders: List<LocalReminder>) = Unit
    override suspend fun cancel(ids: List<String>) = Unit
    override suspend fun cancelAll() = Unit
}
