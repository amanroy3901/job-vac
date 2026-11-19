package com.sarkarimatch.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.force_update
import com.sarkarimatch.app.resources.outage
import com.sarkarimatch.core.designsystem.component.PlaceholderScreen
import com.sarkarimatch.core.model.SetupMode
import com.sarkarimatch.feature.alerts.navigation.AlertSettings
import com.sarkarimatch.feature.alerts.navigation.DeliveryHealth
import com.sarkarimatch.feature.alerts.navigation.Setup
import com.sarkarimatch.feature.alerts.navigation.alertSettingsDestinations
import com.sarkarimatch.feature.alerts.navigation.setupDestinations
import com.sarkarimatch.feature.applications.navigation.formsGraph
import com.sarkarimatch.feature.jobs.navigation.AppliedMoment
import com.sarkarimatch.feature.jobs.navigation.JobDetail
import com.sarkarimatch.feature.applications.ui.AppliedMomentRoute
import androidx.navigation.toRoute
import com.sarkarimatch.feature.jobs.navigation.JobsGraph
import com.sarkarimatch.feature.jobs.navigation.jobsGraph
import com.sarkarimatch.feature.onboarding.navigation.EditProfile
import com.sarkarimatch.feature.onboarding.navigation.LanguageSettings
import com.sarkarimatch.feature.onboarding.navigation.Onb
import com.sarkarimatch.feature.onboarding.navigation.OnboardingGraph
import com.sarkarimatch.feature.onboarding.navigation.Privacy
import com.sarkarimatch.feature.onboarding.navigation.onboardingGraph
import com.sarkarimatch.feature.onboarding.navigation.onboardingSharedDestinations
import com.sarkarimatch.feature.pro.navigation.Paywall
import com.sarkarimatch.feature.pro.navigation.proDestinations
import com.sarkarimatch.feature.profile.navigation.familyRootDestinations
import com.sarkarimatch.feature.profile.navigation.profileGraph
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

@Serializable data object MainGraph
@Serializable data object ForceUpdate // S41
@Serializable data object Outage // S42

/**
 * The single NavHost (navigation.md §4). Only :app knows every feature; features expose graphs + callbacks and
 * never depend on each other. Shared destinations (S13–S16, S27, S40, S47, S35, S49) are registered once at the
 * root and take a `mode` argument where their CTA differs.
 */
@Composable
fun SmNavHost(
    navController: NavHostController,
    startOnboarding: Boolean,
    onOnboardingFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(
        navController = navController,
        startDestination = if (startOnboarding) OnboardingGraph else MainGraph,
        modifier = modifier,
        // motion.usage: forward = fade 300 ms + slide 1/12; back = reverse.
        enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() },
    ) {
        onboardingGraph(
            navController = navController,
            onOpenPrivacy = { navController.navigate(Privacy(SetupMode.ONBOARDING)) },
            onProfileDone = { navController.navigate(Setup.NotifPrimer(SetupMode.ONBOARDING)) },
        )
        onboardingSharedDestinations(
            navController = navController,
            onPrivacyAcknowledged = { mode ->
                if (mode == SetupMode.ONBOARDING) navController.navigate(Onb.Exams) else navController.popBackStack()
            },
        )
        setupDestinations(
            navController = navController,
            onOnboardingSetupDone = {
                onOnboardingFinished()
                navController.navigate(MainGraph) { popUpTo(OnboardingGraph) { inclusive = true } }
            },
        )
        familyRootDestinations(navController)

        navigation<MainGraph>(startDestination = JobsGraph) {
            jobsGraph(
                onOpenJob = { navController.navigate(JobDetail(it)) },
                onAppliedMoment = { navController.navigate(AppliedMoment(it)) },
                onOpenPaywall = { navController.navigate(Paywall(it)) },
                onOpenDeliveryHealth = { navController.navigate(DeliveryHealth) },
                onEditProfileField = { navController.navigate(EditProfile(it)) },
                onBack = back,
            )
            formsGraph(onOpenJob = { navController.navigate(JobDetail(it)) }, onBack = back)
            composable<AppliedMoment> { entry ->
                AppliedMomentRoute(matchId = entry.toRoute<AppliedMoment>().matchId, onClose = back)
            }
            profileGraph(
                navController = navController,
                onOpenLanguage = { navController.navigate(LanguageSettings) },
                onOpenAlertSettings = { navController.navigate(AlertSettings) },
                nestedDestinations = { alertSettingsDestinations(navController) },
            )
            proDestinations(onBack = back)
        }

        composable<ForceUpdate> { PlaceholderScreen("S41", stringResource(Res.string.force_update)) }
        composable<Outage> { PlaceholderScreen("S42", stringResource(Res.string.outage)) }
    }
}
