package com.sarkarimatch.feature.alerts.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.alerts_delivery_health
import com.sarkarimatch.app.resources.alerts_done
import com.sarkarimatch.app.resources.alerts_exact_alarm
import com.sarkarimatch.app.resources.alerts_notif_primer
import com.sarkarimatch.app.resources.alerts_oem_guide
import com.sarkarimatch.app.resources.alerts_self_test
import com.sarkarimatch.app.resources.alerts_settings
import com.sarkarimatch.core.designsystem.component.PlaceholderScreen
import com.sarkarimatch.core.designsystem.component.continueLabel
import com.sarkarimatch.core.model.SetupMode
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** S13–S16, shared by onboarding and settings; `mode` only changes the CTA (navigation.md §2). */
object Setup {
    @Serializable data class NotifPrimer(val mode: SetupMode) // S13
    @Serializable data class ExactAlarm(val mode: SetupMode) // S14
    @Serializable data class OemGuide(val mode: SetupMode, val brand: String? = null) // S15
    @Serializable data class SelfTest(val mode: SetupMode) // S16
}

@Serializable data object AlertSettings // S28
@Serializable data object DeliveryHealth // S29

/**
 * S13 → S14 → S15 → S16. Registered once at the root of the NavHost (not twice as in the design note), because
 * the same type-safe route in two nested graphs is ambiguous; the `mode` argument carries the context instead.
 * In ONBOARDING mode the last step calls [onOnboardingSetupDone]; in SETTINGS mode each step pops back.
 */
fun NavGraphBuilder.setupDestinations(navController: NavController, onOnboardingSetupDone: () -> Unit) {
    composable<Setup.NotifPrimer>(deepLinks = listOf(navDeepLink<Setup.NotifPrimer>(basePath = "sarkarimatch://setup/notifications"))) {
        val mode = it.toRoute<Setup.NotifPrimer>().mode
        SetupStep("S13", Res.string.alerts_notif_primer, mode, navController) { navController.navigate(Setup.ExactAlarm(mode)) }
    }
    composable<Setup.ExactAlarm> {
        val mode = it.toRoute<Setup.ExactAlarm>().mode
        SetupStep("S14", Res.string.alerts_exact_alarm, mode, navController) { navController.navigate(Setup.OemGuide(mode)) }
    }
    composable<Setup.OemGuide> {
        val mode = it.toRoute<Setup.OemGuide>().mode
        SetupStep("S15", Res.string.alerts_oem_guide, mode, navController) { navController.navigate(Setup.SelfTest(mode)) }
    }
    composable<Setup.SelfTest>(deepLinks = listOf(navDeepLink<Setup.SelfTest>(basePath = "sarkarimatch://test"))) {
        val mode = it.toRoute<Setup.SelfTest>().mode
        SetupStep("S16", Res.string.alerts_self_test, mode, navController, onOnboardingSetupDone)
    }
}

/** S28, S29: nested inside the Profile tab graph by :app. */
fun NavGraphBuilder.alertSettingsDestinations(navController: NavController) {
    composable<AlertSettings> {
        PlaceholderScreen("S28", stringResource(Res.string.alerts_settings), onBack = navController::popBackStack)
    }
    composable<DeliveryHealth>(deepLinks = listOf(navDeepLink<DeliveryHealth>(basePath = "sarkarimatch://delivery"))) {
        PlaceholderScreen(
            screenId = "S29",
            title = stringResource(Res.string.alerts_delivery_health),
            onBack = navController::popBackStack,
            primaryLabel = stringResource(Res.string.alerts_self_test),
            onPrimary = { navController.navigate(Setup.SelfTest(SetupMode.SETTINGS)) },
        )
    }
}

@androidx.compose.runtime.Composable
private fun SetupStep(id: String, title: StringResource, mode: SetupMode, navController: NavController, onNext: () -> Unit) {
    val onboarding = mode == SetupMode.ONBOARDING
    val pop: () -> Unit = { navController.popBackStack() }
    PlaceholderScreen(
        screenId = id,
        title = stringResource(title),
        onBack = navController::popBackStack,
        primaryLabel = if (onboarding) continueLabel() else stringResource(Res.string.alerts_done),
        onPrimary = if (onboarding) onNext else pop,
    )
}
