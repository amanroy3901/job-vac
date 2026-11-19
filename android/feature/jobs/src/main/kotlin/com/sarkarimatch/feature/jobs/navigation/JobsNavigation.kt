package com.sarkarimatch.feature.jobs.navigation

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import com.sarkarimatch.core.designsystem.component.PlaceholderScreen
import com.sarkarimatch.core.model.ArchiveTab
import com.sarkarimatch.core.model.PaywallSource
import com.sarkarimatch.core.model.ProfileField
import com.sarkarimatch.feature.jobs.R
import com.sarkarimatch.feature.jobs.ui.HomeRoute
import kotlinx.serialization.Serializable

@Serializable data object JobsGraph // tab 1
@Serializable data object Home // S20
@Serializable data class JobDetail(val matchId: String, val src: String? = null) // S21
@Serializable data class SharedPost(val postId: String) // S21 via /p/
@Serializable data object Inbox // S24 (P1)
@Serializable data class Archive(val tab: ArchiveTab = ArchiveTab.NotInterested) // S25 (P1)
@Serializable data class Digest(val date: String) // S44 (P1)

@Suppress("UnusedParameter") // onEditProfileField is used by S21 (FE-17)
fun NavGraphBuilder.jobsGraph(
    onOpenJob: (matchId: String) -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onOpenDeliveryHealth: () -> Unit,
    onEditProfileField: (ProfileField) -> Unit,
    onBack: () -> Unit,
) {
    navigation<JobsGraph>(startDestination = Home) {
        composable<Home>(deepLinks = listOf(navDeepLink<Home>(basePath = "sarkarimatch://home"))) {
            HomeRoute(onOpenJob = onOpenJob, onOpenPaywall = onOpenPaywall, onOpenDeliveryHealth = onOpenDeliveryHealth)
        }
        composable<JobDetail>(
            deepLinks = listOf(
                navDeepLink<JobDetail>(basePath = "https://sarkarimatch.in/j"),
                navDeepLink<JobDetail>(basePath = "sarkarimatch://job"),
                navDeepLink<JobDetail>(basePath = "sarkarimatch://match"),
            ),
        ) {
            PlaceholderScreen("S21", stringResource(R.string.jobs_job_detail), onBack = onBack)
        }
        composable<SharedPost>(deepLinks = listOf(navDeepLink<SharedPost>(basePath = "https://sarkarimatch.in/p"))) {
            PlaceholderScreen("S21", stringResource(R.string.jobs_job_detail), onBack = onBack)
        }
        composable<Inbox>(deepLinks = listOf(navDeepLink<Inbox>(basePath = "sarkarimatch://inbox"))) {
            PlaceholderScreen("S24", stringResource(R.string.jobs_inbox), onBack = onBack)
        }
        composable<Archive> { PlaceholderScreen("S25", stringResource(R.string.jobs_archive), onBack = onBack) }
        composable<Digest>(deepLinks = listOf(navDeepLink<Digest>(basePath = "sarkarimatch://digest"))) {
            PlaceholderScreen("S44", stringResource(R.string.jobs_digest), onBack = onBack)
        }
    }
}
