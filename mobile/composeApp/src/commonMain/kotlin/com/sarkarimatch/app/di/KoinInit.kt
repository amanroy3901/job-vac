package com.sarkarimatch.app.di

import com.sarkarimatch.app.StartViewModel
import com.sarkarimatch.core.data.AppConfig
import com.sarkarimatch.core.data.di.AppScope
import com.sarkarimatch.core.data.di.dataModule
import com.sarkarimatch.feature.applications.ui.AppliedMomentViewModel
import com.sarkarimatch.feature.applications.ui.MyFormsViewModel
import com.sarkarimatch.feature.jobs.ui.JobDetailViewModel
import com.sarkarimatch.feature.onboarding.ui.DobViewModel
import org.koin.core.module.dsl.viewModel
import com.sarkarimatch.feature.jobs.ui.HomeViewModel
import com.sarkarimatch.feature.onboarding.ui.LanguageViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** ViewModels of every screen (Koin replaces Hilt, which is Android-only). */
val uiModule: Module = module {
    viewModel { StartViewModel(get(), get(), get(AppScope)) }
    viewModelOf(::LanguageViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::DobViewModel)
    viewModelOf(::MyFormsViewModel)
    viewModel { (matchId: String) -> JobDetailViewModel(matchId, get(), get()) }
    viewModel { (matchId: String) -> AppliedMomentViewModel(matchId, get(), get()) }
}

/**
 * Starts Koin once per process. [platformModule] is `androidDataModule(…)` on Android and `iosDataModule(bridges)`
 * on iOS; [declaration] adds platform setup such as `androidContext(…)`.
 */
fun initKoin(config: AppConfig, platformModule: Module, declaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        declaration()
        modules(dataModule(config), platformModule, uiModule)
    }
