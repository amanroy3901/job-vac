package com.sarkarimatch.core.data.di

import com.sarkarimatch.core.ads.AdsGate
import com.sarkarimatch.core.auth.DevIdTokenProvider
import com.sarkarimatch.core.auth.IdTokenProvider
import com.sarkarimatch.core.auth.NoIdTokenProvider
import com.sarkarimatch.core.data.AppConfig
import com.sarkarimatch.core.data.account.AccountRepository
import com.sarkarimatch.core.data.account.ConfigRepository
import com.sarkarimatch.core.data.catalog.CatalogMatcher
import com.sarkarimatch.core.data.catalog.CatalogSync
import com.sarkarimatch.core.data.catalog.MatchEngine
import com.sarkarimatch.core.data.catalog.SyncCoordinator
import com.sarkarimatch.core.data.prefs.UserPreferencesRepository
import com.sarkarimatch.core.data.profile.ProfileDraft
import com.sarkarimatch.core.data.profile.ProfileRepository
import com.sarkarimatch.core.data.repository.MatchActionRepository
import com.sarkarimatch.core.data.repository.MatchRepository
import com.sarkarimatch.core.data.repository.RoomMatchRepository
import com.sarkarimatch.core.database.SmDatabase
import com.sarkarimatch.core.eligibility.EligibilityEvaluator
import com.sarkarimatch.core.eligibility.QualificationTaxonomy
import com.sarkarimatch.core.model.AppLanguage
import com.sarkarimatch.core.network.SmApi
import com.sarkarimatch.core.network.createHttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Process-wide scope for work that outlives screens (prefs mirrors, sync). */
val AppScope = named("appScope")

/** The app language as a hot value for non-Compose code (Accept-Language, notification text). */
class CurrentLanguage(val value: StateFlow<AppLanguage>)

/**
 * Koin bindings shared by Android, iOS and the desktop dev target. The platform module adds what differs:
 * HttpClientEngine, SmDatabase builder, DataStore path, and the platform services (billing, auth, push, reminders,
 * ads, language). See `androidDataModule()` (androidMain), `iosDataModule(bridges)` (iosMain), `desktopDataModule`.
 */
fun dataModule(config: AppConfig): Module = module {
    single { config }
    single(AppScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { UserPreferencesRepository(get()) }
    single {
        CurrentLanguage(
            get<UserPreferencesRepository>().appLanguage
                .stateIn(get<CoroutineScope>(AppScope), SharingStarted.Eagerly, AppLanguage.DEFAULT),
        )
    }
    // Dev builds (Android `dev` flavour, desktop) use the backend's dev token; others wait for Firebase (FE-06).
    single<IdTokenProvider> {
        if (config.devAuth) {
            val prefs = get<UserPreferencesRepository>()
            DevIdTokenProvider { prefs.devUid() }
        } else {
            NoIdTokenProvider()
        }
    }
    single { createHttpClient(get<HttpClientEngine>(), config) { get<CurrentLanguage>().value.value.tag } }
    single { SmApi(get(), get()) }
    single { get<SmDatabase>().matchDao() }
    single { get<SmDatabase>().profileDao() }
    single { get<SmDatabase>().catalogDao() }
    single { get<SmDatabase>().accountDao() }
    // Built-in taxonomy until the ReferenceBundle (GET /v1/reference) is cached in Room (FE-11).
    single { EligibilityEvaluator(QualificationTaxonomy.Builtin) }
    single { MatchEngine(get()) }
    single { ProfileRepository(get()) }
    single { ProfileDraft() }
    single { CatalogMatcher(get(), get(), get(), get()) }
    single { CatalogSync(get(), get(), get(), language = { get<CurrentLanguage>().value.value }) }
    single { AccountRepository(get(), get(), get(), language = { get<CurrentLanguage>().value.value }) }
    single { ConfigRepository(get(), get()) }
    single { SyncCoordinator(get(), get(), get()) }
    single<MatchRepository> { RoomMatchRepository(get(), get(), get(), get()) }
    single { MatchActionRepository(get()) }
    single { AdsGate(get()) }
}
