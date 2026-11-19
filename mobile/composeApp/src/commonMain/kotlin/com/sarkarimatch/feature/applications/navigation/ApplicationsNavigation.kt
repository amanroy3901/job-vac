package com.sarkarimatch.feature.applications.navigation

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import com.sarkarimatch.core.designsystem.component.PlaceholderScreen
import com.sarkarimatch.feature.applications.R
import kotlinx.serialization.Serializable

@Serializable data object FormsGraph // tab 2
@Serializable data object Applied // S22
@Serializable data class Timeline(val applicationId: String) // S23
@Serializable data class Clash(val clashId: String) // S37 (P1)

/** Tab 2. S22 → S23 navigation is wired when S22 lists real applications (FE-22). */
fun NavGraphBuilder.formsGraph(onBack: () -> Unit) {
    navigation<FormsGraph>(startDestination = Applied) {
        composable<Applied>(deepLinks = listOf(navDeepLink<Applied>(basePath = "sarkarimatch://forms"))) {
            PlaceholderScreen("S22", stringResource(R.string.apps_applied))
        }
        composable<Timeline>(deepLinks = listOf(navDeepLink<Timeline>(basePath = "https://sarkarimatch.in/a"))) {
            PlaceholderScreen("S23", stringResource(R.string.apps_timeline), onBack = onBack)
        }
        composable<Clash>(deepLinks = listOf(navDeepLink<Clash>(basePath = "sarkarimatch://clash"))) {
            PlaceholderScreen("S37", stringResource(R.string.apps_clash), onBack = onBack)
        }
    }
}
