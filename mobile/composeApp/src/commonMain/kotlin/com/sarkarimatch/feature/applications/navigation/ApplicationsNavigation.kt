package com.sarkarimatch.feature.applications.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.apps_applied
import com.sarkarimatch.app.resources.apps_clash
import com.sarkarimatch.app.resources.apps_timeline
import com.sarkarimatch.core.designsystem.component.PlaceholderScreen
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

@Serializable data object FormsGraph // tab 2
@Serializable data object Applied // S22
@Serializable data class Timeline(val applicationId: String) // S23
@Serializable data class Clash(val clashId: String) // S37 (P1)

/** Tab 2. S22 → S23 navigation is wired when S22 lists real applications (FE-22). */
fun NavGraphBuilder.formsGraph(onBack: () -> Unit) {
    navigation<FormsGraph>(startDestination = Applied) {
        composable<Applied>(deepLinks = listOf(navDeepLink<Applied>(basePath = "sarkarimatch://forms"))) {
            PlaceholderScreen("S22", stringResource(Res.string.apps_applied))
        }
        composable<Timeline>(deepLinks = listOf(navDeepLink<Timeline>(basePath = "https://sarkarimatch.in/a"))) {
            PlaceholderScreen("S23", stringResource(Res.string.apps_timeline), onBack = onBack)
        }
        composable<Clash>(deepLinks = listOf(navDeepLink<Clash>(basePath = "sarkarimatch://clash"))) {
            PlaceholderScreen("S37", stringResource(Res.string.apps_clash), onBack = onBack)
        }
    }
}
