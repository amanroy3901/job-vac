package com.sarkarimatch.feature.pro.navigation

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.sarkarimatch.core.designsystem.component.PlaceholderScreen
import com.sarkarimatch.core.model.PaywallSource
import com.sarkarimatch.feature.pro.R
import kotlinx.serialization.Serializable

@Serializable data class Paywall(val source: PaywallSource, val plan: String? = null) // S30
@Serializable data class PaymentStatus(val purchaseRef: String) // S31
@Serializable data object ManagePlan // S32

/** S30–S32 sit directly in MainGraph (not in a tab) so any tab can push them (navigation.md §2). */
fun NavGraphBuilder.proDestinations(onBack: () -> Unit) {
    composable<Paywall>(deepLinks = listOf(navDeepLink<Paywall>(basePath = "https://sarkarimatch.in/pro"))) {
        PlaceholderScreen("S30", stringResource(R.string.pro_paywall), onBack = onBack)
    }
    composable<PaymentStatus> { PlaceholderScreen("S31", stringResource(R.string.pro_payment_status), onBack = onBack) }
    composable<ManagePlan>(deepLinks = listOf(navDeepLink<ManagePlan>(basePath = "sarkarimatch://plan"))) {
        PlaceholderScreen("S32", stringResource(R.string.pro_manage_plan), onBack = onBack)
    }
}
