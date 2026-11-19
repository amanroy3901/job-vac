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
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * iOS implementations: Darwin HTTP engine, Room + DataStore files in [storageDirectory], Swift bridges or stubs.
 * Every bridge is nullable and falls back to a stub (free plan, guest only, no push, no ads).
 */
fun iosDataModule(bridges: IosPlatformBridges): Module = module {
    single { bridges }
    single<HttpClientEngine> { Darwin.create() }
    single { buildSmDatabase(Room.databaseBuilder<SmDatabase>(name = storageDirectory() + "/" + DATABASE_NAME)) }
    single { createAppPrefsDataStore(storageDirectory() + "/" + APP_PREFS_FILE) }
    single<LanguageController> { PrefsLanguageController(get()) }
    single<BillingService> { bridges.billing?.let { IosBillingService(it) } ?: StubBillingService() }
    single<AuthService> { bridges.auth?.let { IosAuthService(it) } ?: GuestOnlyAuthService() }
    single<PushService> { bridges.push?.let { IosPushService(it) } ?: NoPushService() }
    single<ReminderScheduler> { IosReminderScheduler() }
    single<AdsService> { bridges.ads?.let { IosAdsService(it) } ?: NoAdsService() }
}

/** App Group shared with the P1 Notification Service Extension (iosApp.entitlements). */
const val IOS_APP_GROUP = "group.in.sarkarimatch.app"

/**
 * Where the database and DataStore live (data-layer.md §3): `<App Group container>/SarkariMatch`, or
 * `Library/Application Support/SarkariMatch` when the App Group entitlement is missing (e.g. unsigned simulator
 * builds). Never `Documents/` (iCloud device backup, Files app). The directory is excluded from iCloud/iTunes backup
 * (restore goes through our encrypted backup only) and uses CompleteUntilFirstUserAuthentication protection so
 * notification actions and background tasks can write while the phone is locked.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun storageDirectory(): String {
    val fm = NSFileManager.defaultManager
    val base: NSURL = fm.containerURLForSecurityApplicationGroupIdentifier(IOS_APP_GROUP)
        ?: requireNotNull(
            fm.URLForDirectory(
                directory = NSApplicationSupportDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            ),
        ) { "no Application Support directory" }
    val dir = requireNotNull(base.URLByAppendingPathComponent("SarkariMatch")) { "bad storage path" }
    fm.createDirectoryAtURL(
        dir,
        withIntermediateDirectories = true,
        attributes = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
        error = null,
    )
    dir.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return requireNotNull(dir.path) { "bad storage path" }
}
