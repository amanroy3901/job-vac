package com.sarkarimatch.feature.onboarding.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.onb_category
import com.sarkarimatch.app.resources.onb_dob
import com.sarkarimatch.app.resources.onb_edit_profile
import com.sarkarimatch.app.resources.onb_exams
import com.sarkarimatch.app.resources.onb_more
import com.sarkarimatch.app.resources.onb_privacy
import com.sarkarimatch.app.resources.onb_qualification
import com.sarkarimatch.app.resources.onb_result
import com.sarkarimatch.app.resources.onb_state
import com.sarkarimatch.app.resources.onb_welcome
import com.sarkarimatch.core.designsystem.component.PlaceholderScreen
import com.sarkarimatch.core.designsystem.component.continueLabel
import com.sarkarimatch.core.model.ProfileField
import com.sarkarimatch.core.model.SetupMode
import com.sarkarimatch.feature.onboarding.ui.LanguageRoute
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Public routes (navigation.md §3). Args are IDs/enums only.
@Serializable data object OnboardingGraph

object Onb {
    @Serializable data object Language // S02
    @Serializable data object Welcome // S03
    @Serializable data object Exams // S04
    @Serializable data object Qualification // S05
    @Serializable data object Dob // S06
    @Serializable data object Category // S07
    @Serializable data object State // S08
    @Serializable data object More // S09
    @Serializable data object Result // S10
}

/** S47; registered once at the root (used from onboarding and from Profile → Account). */
@Serializable data class Privacy(val mode: SetupMode = SetupMode.ONBOARDING)

/** S27 (this module owns the whole profile form, create and edit). */
@Serializable data class EditProfile(val field: ProfileField)

/** S40 (same LanguageViewModel as S02). */
@Serializable data object LanguageSettings

/**
 * S02 → S03 → S47 → S04…S09 → S10, then [onProfileDone] (the app continues with the S13–S16 setup in
 * :feature:alerts). S47 sits at the root, so the onboarding graph hands over to it via [onOpenPrivacy].
 */
fun NavGraphBuilder.onboardingGraph(
    navController: NavController,
    onOpenPrivacy: () -> Unit,
    onProfileDone: () -> Unit,
) {
    navigation<OnboardingGraph>(startDestination = Onb.Language) {
        composable<Onb.Language> {
            LanguageRoute(onContinue = { navController.navigate(Onb.Welcome) })
        }
        step<Onb.Welcome>("S03", Res.string.onb_welcome, navController) { onOpenPrivacy() }
        step<Onb.Exams>("S04", Res.string.onb_exams, navController) { navController.navigate(Onb.Qualification) }
        step<Onb.Qualification>("S05", Res.string.onb_qualification, navController) { navController.navigate(Onb.Dob) }
        step<Onb.Dob>("S06", Res.string.onb_dob, navController) { navController.navigate(Onb.Category) }
        step<Onb.Category>("S07", Res.string.onb_category, navController) { navController.navigate(Onb.State) }
        step<Onb.State>("S08", Res.string.onb_state, navController) { navController.navigate(Onb.More) }
        step<Onb.More>("S09", Res.string.onb_more, navController) { navController.navigate(Onb.Result) }
        step<Onb.Result>("S10", Res.string.onb_result, navController, onProfileDone)
    }
}

/** Root-level destinations owned by this module: S47 Privacy, S27 EditProfile, S40 LanguageSettings. */
fun NavGraphBuilder.onboardingSharedDestinations(
    navController: NavController,
    onPrivacyAcknowledged: (SetupMode) -> Unit,
) {
    composable<Privacy>(deepLinks = listOf(navDeepLink<Privacy>(basePath = "sarkarimatch://privacy"))) { entry ->
        val mode = entry.toRoute<Privacy>().mode
        PlaceholderScreen(
            screenId = "S47",
            title = stringResource(Res.string.onb_privacy),
            onBack = navController::popBackStack,
            primaryLabel = continueLabel(),
            onPrimary = { onPrivacyAcknowledged(mode) },
        )
    }
    composable<EditProfile> {
        PlaceholderScreen("S27", stringResource(Res.string.onb_edit_profile), onBack = navController::popBackStack)
    }
    composable<LanguageSettings> {
        LanguageRoute(onContinue = navController::popBackStack, onBack = navController::popBackStack)
    }
}

private inline fun <reified T : Any> NavGraphBuilder.step(
    id: String,
    title: StringResource,
    navController: NavController,
    noinline onNext: () -> Unit,
) {
    composable<T> {
        PlaceholderScreen(
            screenId = id,
            title = stringResource(title),
            onBack = navController::popBackStack,
            primaryLabel = continueLabel(),
            onPrimary = onNext,
        )
    }
}
