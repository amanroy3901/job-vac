package com.sarkarimatch.core.data.di

import android.app.Application
import androidx.room.Room
import com.sarkarimatch.core.ads.AdsService
import com.sarkarimatch.core.ads.NoAdsService
import com.sarkarimatch.core.auth.AuthService
import com.sarkarimatch.core.auth.CredentialManagerAuthService
import com.sarkarimatch.core.billing.BillingService
import com.sarkarimatch.core.billing.PlayBillingService
import com.sarkarimatch.core.data.prefs.APP_PREFS_FILE
import com.sarkarimatch.core.data.prefs.AndroidLanguageController
import com.sarkarimatch.core.data.prefs.LanguageController
import com.sarkarimatch.core.data.prefs.createAppPrefsDataStore
import com.sarkarimatch.core.database.DATABASE_NAME
import com.sarkarimatch.core.database.SmDatabase
import com.sarkarimatch.core.database.buildSmDatabase
import com.sarkarimatch.core.notifications.NotificationChannels
import com.sarkarimatch.core.notifications.ReminderScheduler
import com.sarkarimatch.core.notifications.WorkManagerReminderScheduler
import com.sarkarimatch.core.platform.ActivityProvider
import com.sarkarimatch.core.push.FcmPushService
import com.sarkarimatch.core.push.PushService
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Android implementations of the platform services. [googleServerClientId] is the backend's OAuth web client id for
 * Sign in with Google (blank → guest only).
 */
fun androidDataModule(googleServerClientId: String?): Module = module {
    single(createdAtStart = true) { ActivityProvider(androidContext() as Application) }
    single<HttpClientEngine> { OkHttp.create() }
    single {
        val path = androidContext().getDatabasePath(DATABASE_NAME).absolutePath
        buildSmDatabase(Room.databaseBuilder<SmDatabase>(androidContext(), path))
    }
    single { createAppPrefsDataStore(androidContext().filesDir.resolve("datastore/$APP_PREFS_FILE").absolutePath) }
    single { AndroidLanguageController(get()) } bind LanguageController::class
    single<BillingService> { PlayBillingService(androidContext(), get()) }
    single<AuthService> { CredentialManagerAuthService(androidContext(), get(), googleServerClientId) }
    single<PushService> { FcmPushService(androidContext()) }
    single { NotificationChannels() }
    single<ReminderScheduler> { WorkManagerReminderScheduler(androidContext(), get()) }
    single<AdsService> { NoAdsService() } // Google Mobile Ads Next-Gen SDK: FE-27
}
