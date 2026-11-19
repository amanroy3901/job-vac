package com.sarkarimatch.feature.pro.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.pro_manage_plan
import com.sarkarimatch.app.resources.pro_payment_status
import com.sarkarimatch.app.resources.pro_paywall
import com.sarkarimatch.core.designsystem.component.PlaceholderScreen
import com.sarkarimatch.core.model.PaywallSource
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

@Serializable data class Paywall(val source: PaywallSource, val plan: String? = null) // S30
@Serializable data class PaymentStatus(val purchaseRef: String) // S31
@Serializable data object ManagePlan // S32

/** S30–S32 sit directly in MainGraph (not in a tab) so any tab can push them (navigation.md §2). */
fun NavGraphBuilder.proDestinations(onBack: () -> Unit) {
    composable<Paywall>(deepLinks = listOf(navDeepLink<Paywall>(basePath = "https://sarkarimatch.in/pro"))) {
        PlaceholderScreen("S30", stringResource(Res.string.pro_paywall), onBack = onBack)
    }
    composable<PaymentStatus> { PlaceholderScreen("S31", stringResource(Res.string.pro_payment_status), onBack = onBack) }
    composable<ManagePlan>(deepLinks = listOf(navDeepLink<ManagePlan>(basePath = "sarkarimatch://plan"))) {
        PlaceholderScreen("S32", stringResource(Res.string.pro_manage_plan), onBack = onBack)
    }
}
