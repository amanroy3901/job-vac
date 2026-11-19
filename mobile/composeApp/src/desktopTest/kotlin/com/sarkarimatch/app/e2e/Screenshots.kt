package com.sarkarimatch.app.e2e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.sarkarimatch.app.TopLevelTab
import com.sarkarimatch.core.data.account.AccountRepository
import com.sarkarimatch.core.data.repository.JobDetailUi
import com.sarkarimatch.core.data.repository.MatchRepository
import com.sarkarimatch.core.designsystem.component.ProvideAppLocale
import com.sarkarimatch.core.designsystem.component.SmNavigationBar
import com.sarkarimatch.core.designsystem.component.SmNavigationItem
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.model.AppLanguage
import com.sarkarimatch.core.model.Ist
import com.sarkarimatch.core.model.MatchCardUi
import com.sarkarimatch.core.network.EntitlementDto
import com.sarkarimatch.feature.applications.ui.AppliedMomentScreen
import com.sarkarimatch.feature.applications.ui.AppliedMomentUi
import com.sarkarimatch.feature.applications.ui.MyFormsScreen
import com.sarkarimatch.feature.applications.ui.MyFormsUi
import com.sarkarimatch.feature.jobs.ui.HomeScreen
import com.sarkarimatch.feature.jobs.ui.HomeViewModel
import com.sarkarimatch.feature.jobs.ui.JobDetailScreen
import com.sarkarimatch.feature.onboarding.ui.DobScreen
import com.sarkarimatch.feature.onboarding.ui.DobUiState
import com.sarkarimatch.feature.onboarding.ui.LanguageScreen
import com.sarkarimatch.feature.onboarding.ui.LanguageUiState
import java.io.File
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.EncodedImageFormat
import org.koin.core.Koin

/**
 * Renders the real screens headlessly (Compose `ImageComposeScene`, Skia on the JVM) with the live data of the e2e
 * run, in a 412 × 892 dp phone frame at 2× (tall frames for long screens).
 */
class Screenshots(private val koin: Koin, private val outDir: File) {
    private val today = Ist.today()

    @Suppress("LongParameterList")
    suspend fun renderAll(
        feedBeforeApplied: List<MatchCardUi>,
        upPoliceMatchId: String,
        upPoliceDetailBeforeApplied: JobDetailUi,
        ineligibleMatchId: String,
        checkMatchId: String,
        freeEntitlement: EntitlementDto,
    ) {
        outDir.mkdirs()
        val repo = koin.get<MatchRepository>()
        val entitlement = koin.get<AccountRepository>().entitlement.first()
        val applied = repo.observeApplied().first()
        val ineligible = requireNotNull(repo.observeDetail(ineligibleMatchId).first())
        val check = requireNotNull(repo.observeDetail(checkMatchId).first())
        val home = HomeViewModel.group(feedBeforeApplied, today)
        for (lang in listOf(AppLanguage.HI, AppLanguage.EN)) {
            val t = lang.tag
            shot("01-language-$t", lang) { LanguageScreen(LanguageUiState(selected = lang), {}, {}) }
            shot("02-onboarding-dob-$t", lang) { DobScreen(DobUiState("15", "05", "2003", today), {}, {}, {}, {}, {}) }
            shot("03-home-$t", lang) { Tabs(TopLevelTab.JOBS) { HomeScreen(home, {}, {}, {}, {}) } }
            shot("03-home-full-$t", lang, heightDp = 2000) { Tabs(TopLevelTab.JOBS) { HomeScreen(home, {}, {}, {}, {}) } }
            shot("04-job-detail-eligible-$t", lang, heightDp = 1500) { JobDetailScreen(upPoliceDetailBeforeApplied, today, {}, {}, {}) }
            shot("04-job-detail-check-$t", lang, heightDp = 1500) { JobDetailScreen(check, today, {}, {}, {}) }
            shot("04-job-detail-not-eligible-$t", lang, heightDp = 1300) { JobDetailScreen(ineligible, today, {}, {}, {}) }
            shot("05-applied-trial-offer-$t", lang) { AppliedMomentScreen(
                AppliedMomentUi(upPoliceDetailBeforeApplied.title, freeEntitlement),
                {},
                {},
            ) }
            shot("06-applied-trial-on-$t", lang) { AppliedMomentScreen(
                AppliedMomentUi(upPoliceDetailBeforeApplied.title, entitlement),
                {},
                {},
            ) }
            shot("07-my-forms-$t", lang) { Tabs(TopLevelTab.FORMS) { MyFormsScreen(MyFormsUi(applied, entitlement, loading = false), {}) } }
        }
        println("[e2e] screenshots → ${outDir.absolutePath} (${outDir.list()?.size} files); applied form: $upPoliceMatchId")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun shot(name: String, language: AppLanguage, widthDp: Int = 412, heightDp: Int = 892, content: @Composable () -> Unit) {
        val density = 2f
        val scene = ImageComposeScene(
            width = (widthDp * density).toInt(),
            height = (heightDp * density).toInt(),
            density = Density(density),
        ) {
            ProvideAppLocale(language) {
                SmTheme(language = language, darkTheme = false) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        try {
            var frame = 0L
            repeat(FRAMES) {
                scene.render(frame)
                frame += FRAME_NANOS
                Thread.sleep(SETTLE_MS)
            }
            val image = scene.render(frame)
            val png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "png encode $name" }
            File(outDir, "$name.png").writeBytes(png.bytes)
        } finally {
            scene.close()
        }
    }

    /** The app's bottom bar (SmApp scaffold) around a tab root. */
    @Composable
    private fun Tabs(selected: TopLevelTab, content: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            SmNavigationBar {
                TopLevelTab.entries.forEach { tab ->
                    SmNavigationItem(
                        selected = tab == selected,
                        onClick = {},
                        label = stringResource(tab.label),
                        icon = tab.icon,
                        selectedIcon = tab.selectedIcon,
                    )
                }
            }
        }
    }

    private companion object {
        const val FRAMES = 12
        const val FRAME_NANOS = 100_000_000L
        const val SETTLE_MS = 40L
    }
}
