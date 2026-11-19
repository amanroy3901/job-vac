package com.sarkarimatch.core.data.catalog

import com.sarkarimatch.core.data.account.AccountRepository
import com.sarkarimatch.core.data.account.ConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The one entry point for app start / resume / pull-to-refresh (data-layer.md §6 triggers): config, catalog sync +
 * matching, and the entitlement when an account exists. Platform triggers (FCM, WorkManager, BG tasks) call [run].
 */
class SyncCoordinator(
    private val config: ConfigRepository,
    private val catalog: CatalogSync,
    private val accounts: AccountRepository,
) {
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    suspend fun run(): SyncOutcome {
        _syncing.value = true
        try {
            config.refresh()
            val outcome = catalog.sync()
            accounts.refreshEntitlement()
            return outcome
        } finally {
            _syncing.value = false
        }
    }
}
