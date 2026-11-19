package com.sarkarimatch.app.di

import com.sarkarimatch.app.StartViewModel
import com.sarkarimatch.core.data.AppConfig
import com.sarkarimatch.core.data.di.dataModule
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
    viewModelOf(::StartViewModel)
    viewModelOf(::LanguageViewModel)
    viewModelOf(::HomeViewModel)
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
