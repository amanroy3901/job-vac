package com.sarkarimatch.core.data.di

import com.sarkarimatch.core.ads.AdsGate
import com.sarkarimatch.core.data.AppConfig
import com.sarkarimatch.core.data.catalog.MatchEngine
import com.sarkarimatch.core.data.prefs.UserPreferencesRepository
import com.sarkarimatch.core.data.repository.MatchRepository
import com.sarkarimatch.core.data.repository.SampleMatchRepository
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
 * Koin bindings shared by Android and iOS (Hilt is Android-only). The platform module adds what differs:
 * HttpClientEngine, SmDatabase builder, DataStore path, and the platform services (billing, auth, push, reminders,
 * ads, language). See `androidDataModule()` (androidMain) and `iosDataModule(bridges)` (iosMain).
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
    single { createHttpClient(get<HttpClientEngine>(), config) { get<CurrentLanguage>().value.value.tag } }
    single { SmApi(get()) }
    single { get<SmDatabase>().matchDao() }
    single { get<SmDatabase>().profileDao() }
    // Built-in taxonomy until the ReferenceBundle (GET /v1/reference) is cached in Room (FE-11).
    single { EligibilityEvaluator(QualificationTaxonomy.Builtin) }
    single { MatchEngine(get()) }
    single<MatchRepository> { SampleMatchRepository() }
    single { AdsGate(get()) }
}
