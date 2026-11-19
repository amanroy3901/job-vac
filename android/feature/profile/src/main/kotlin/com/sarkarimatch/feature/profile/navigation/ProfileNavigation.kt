package com.sarkarimatch.feature.profile.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import com.sarkarimatch.core.designsystem.component.PlaceholderScreen
import com.sarkarimatch.core.designsystem.component.SmButton
import com.sarkarimatch.core.designsystem.component.SmButtonStyle
import com.sarkarimatch.core.designsystem.component.SmIcons
import com.sarkarimatch.feature.profile.R
import kotlinx.serialization.Serializable

@Serializable data object ProfileGraph // tab 3
@Serializable data object Profile // S26
@Serializable data object Account // S38
@Serializable data object Help // S39
@Serializable data object Certificates // S36 (P1)
@Serializable data object Family // S34 (P1)
@Serializable data class FamilyInvite(val inviteId: String) // S48 (P1)
@Serializable data object Refer // S45 (P1)
@Serializable data object ShareCard // S46 (P1)
@Serializable data object AgeCountdown // S43 (P2)

/** Root-level (work before onboarding; family-only users never onboard). */
@Serializable data class FamilyAccept(val token: String, val key: String) // S35 (P1)
@Serializable data object FamilyFollowing // S49 (P1)

/**
 * Tab 3. [nestedDestinations] lets :app add destinations owned by other features into this tab's back stack
 * (S28/S29 from :feature:alerts) without a feature-to-feature dependency.
 */
fun NavGraphBuilder.profileGraph(
    navController: NavController,
    onOpenLanguage: () -> Unit,
    onOpenAlertSettings: () -> Unit,
    nestedDestinations: NavGraphBuilder.() -> Unit,
) {
    val back: () -> Unit = { navController.popBackStack() }
    navigation<ProfileGraph>(startDestination = Profile) {
        composable<Profile>(deepLinks = listOf(navDeepLink<Profile>(basePath = "sarkarimatch://profile"))) {
            Column(Modifier.fillMaxSize()) {
                PlaceholderScreen("S26", stringResource(R.string.profile_title), modifier = Modifier.weight(1f))
                Column(Modifier.padding(16.dp)) {
                    SmButton(
                        stringResource(R.string.profile_open_language),
                        onOpenLanguage,
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        SmButtonStyle.Outline,
                        SmIcons.Translate,
                    )
                    SmButton(
                        stringResource(R.string.profile_open_alerts),
                        onOpenAlertSettings,
                        Modifier.fillMaxWidth(),
                        SmButtonStyle.Outline,
                        SmIcons.Bell,
                    )
                }
            }
        }
        composable<Account> { PlaceholderScreen("S38", stringResource(R.string.profile_account), onBack = back) }
        composable<Help> { PlaceholderScreen("S39", stringResource(R.string.profile_help), onBack = back) }
        composable<Certificates>(deepLinks = listOf(navDeepLink<Certificates>(basePath = "sarkarimatch://certificates"))) {
            PlaceholderScreen("S36", stringResource(R.string.profile_certificates), onBack = back)
        }
        composable<Family> { PlaceholderScreen("S34", stringResource(R.string.profile_family), onBack = back) }
        composable<FamilyInvite> { PlaceholderScreen("S48", stringResource(R.string.profile_family_invite), onBack = back) }
        composable<Refer> { PlaceholderScreen("S45", stringResource(R.string.profile_refer), onBack = back) }
        composable<ShareCard> { PlaceholderScreen("S46", stringResource(R.string.profile_share_card), onBack = back) }
        composable<AgeCountdown> { PlaceholderScreen("S43", stringResource(R.string.profile_age_countdown), onBack = back) }
        nestedDestinations()
    }
}

/** S35 and S49 live at the root of the NavHost. */
fun NavGraphBuilder.familyRootDestinations(navController: NavController) {
    val back: () -> Unit = { navController.popBackStack() }
    composable<FamilyAccept> { PlaceholderScreen("S35", stringResource(R.string.profile_family_accept), onBack = back) }
    composable<FamilyFollowing>(deepLinks = listOf(navDeepLink<FamilyFollowing>(basePath = "sarkarimatch://family/following"))) {
        PlaceholderScreen("S49", stringResource(R.string.profile_family_following), onBack = back)
    }
}
