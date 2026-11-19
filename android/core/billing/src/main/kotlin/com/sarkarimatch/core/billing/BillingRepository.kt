package com.sarkarimatch.core.billing

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/** Pro entitlement as the app sees it. The server decides; the app never grants Pro itself (billing.md D9). */
enum class Plan { Free, Trial, Pro }

/** Play Billing is the only payment path (founder decision 4). Real implementation: FE-24. */
interface BillingRepository {
    val plan: Flow<Plan>
}

class StubBillingRepository @Inject constructor() : BillingRepository {
    override val plan: Flow<Plan> = MutableStateFlow(Plan.Free)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BillingModule {
    @Binds abstract fun billing(impl: StubBillingRepository): BillingRepository
}
