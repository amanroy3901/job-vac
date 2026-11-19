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
import com.sarkarimatch.core.notifications.IosReminderScheduler
import com.sarkarimatch.core.notifications.ReminderScheduler
import com.sarkarimatch.core.platform.IosAdsService
import com.sarkarimatch.core.platform.IosAuthService
import com.sarkarimatch.core.platform.IosBillingService
import com.sarkarimatch.core.platform.IosPlatformBridges
import com.sarkarimatch.core.platform.IosPushService
import com.sarkarimatch.core.push.NoPushService
import com.sarkarimatch.core.push.PushService
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS implementations: Darwin HTTP engine, Room + DataStore files in Documents, Swift bridges or stubs. */
fun iosDataModule(bridges: IosPlatformBridges): Module = module {
    single<HttpClientEngine> { Darwin.create() }
    single { buildSmDatabase(Room.databaseBuilder<SmDatabase>(name = documentDirectory() + "/" + DATABASE_NAME)) }
    single { createAppPrefsDataStore(documentDirectory() + "/" + APP_PREFS_FILE) }
    single<LanguageController> { PrefsLanguageController(get()) }
    single<BillingService> { bridges.billing?.let { IosBillingService(it) } ?: StubBillingService() }
    single<AuthService> { bridges.auth?.let { IosAuthService(it) } ?: GuestOnlyAuthService() }
    single<PushService> { bridges.push?.let { IosPushService(it) } ?: NoPushService() }
    single<ReminderScheduler> { IosReminderScheduler() }
    single<AdsService> { bridges.ads?.let { IosAdsService(it) } ?: NoAdsService() }
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(url?.path) { "no Documents directory" }
}
