package com.sarkarimatch.app

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sarkarimatch.app.navigation.SmNavHost
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.tab_forms
import com.sarkarimatch.app.resources.tab_jobs
import com.sarkarimatch.app.resources.tab_profile
import com.sarkarimatch.core.designsystem.component.SmIcons
import com.sarkarimatch.core.designsystem.component.SmNavigationBar
import com.sarkarimatch.core.designsystem.component.SmNavigationItem
import com.sarkarimatch.feature.applications.navigation.Applied
import com.sarkarimatch.feature.applications.navigation.FormsGraph
import com.sarkarimatch.feature.jobs.navigation.Home
import com.sarkarimatch.feature.jobs.navigation.JobsGraph
import com.sarkarimatch.feature.profile.navigation.Profile
import com.sarkarimatch.feature.profile.navigation.ProfileGraph
import kotlin.reflect.KClass
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The three bottom tabs (navigation.md §2): Jobs / My forms / Profile. */
enum class TopLevelTab(
    val graph: Any,
    val graphClass: KClass<*>,
    val label: StringResource,
    val icon: DrawableResource,
    val selectedIcon: DrawableResource,
) {
    JOBS(JobsGraph, JobsGraph::class, Res.string.tab_jobs, SmIcons.WorkOutline, SmIcons.Work),
    FORMS(FormsGraph, FormsGraph::class, Res.string.tab_forms, SmIcons.FormsOutline, SmIcons.Forms),
    PROFILE(ProfileGraph, ProfileGraph::class, Res.string.tab_profile, SmIcons.ProfileOutline, SmIcons.Profile),
}

@Composable
fun SmApp(
    startOnboarding: Boolean,
    onOnboardingFinished: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    // Bottom bar only on the three tab roots (inventory).
    val showBottomBar = destination != null &&
        (destination.hasRoute(Home::class) || destination.hasRoute(Applied::class) || destination.hasRoute(Profile::class))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                SmNavigationBar {
                    TopLevelTab.entries.forEach { tab ->
                        SmNavigationItem(
                            selected = destination.isInTab(tab),
                            onClick = { navController.navigateToTab(tab) },
                            label = stringResource(tab.label),
                            icon = tab.icon,
                            selectedIcon = tab.selectedIcon,
                        )
                    }
                }
            }
        },
    ) { padding ->
        SmNavHost(
            navController = navController,
            startOnboarding = startOnboarding,
            onOnboardingFinished = onOnboardingFinished,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}

private fun NavDestination?.isInTab(tab: TopLevelTab): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.graphClass) } == true

/** Standard multiple-back-stack tab switch. */
private fun NavHostController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
