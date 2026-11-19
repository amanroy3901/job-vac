package com.sarkarimatch.feature.applications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.am_done
import com.sarkarimatch.app.resources.am_fineprint
import com.sarkarimatch.app.resources.am_no_ads
import com.sarkarimatch.app.resources.am_not_now
import com.sarkarimatch.app.resources.am_start_trial
import com.sarkarimatch.app.resources.am_step_admit
import com.sarkarimatch.app.resources.am_step_answer
import com.sarkarimatch.app.resources.am_step_dv
import com.sarkarimatch.app.resources.am_step_exam
import com.sarkarimatch.app.resources.am_step_result
import com.sarkarimatch.app.resources.am_subtitle
import com.sarkarimatch.app.resources.am_title
import com.sarkarimatch.app.resources.am_trial_badge
import com.sarkarimatch.app.resources.am_trial_body
import com.sarkarimatch.app.resources.am_trial_error
import com.sarkarimatch.app.resources.am_trial_on
import com.sarkarimatch.app.resources.am_trial_on_body
import com.sarkarimatch.app.resources.am_trial_title
import com.sarkarimatch.core.data.account.AccountRepository
import com.sarkarimatch.core.data.repository.MatchRepository
import com.sarkarimatch.core.designsystem.component.SmButton
import com.sarkarimatch.core.designsystem.component.SmIcon
import com.sarkarimatch.core.designsystem.component.SmIcons
import com.sarkarimatch.core.designsystem.component.currentAppLanguage
import com.sarkarimatch.core.designsystem.theme.SmRadius
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.model.LocalizedText
import com.sarkarimatch.core.model.SmFormat
import com.sarkarimatch.core.network.EntitlementDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

data class AppliedMomentUi(
    val jobTitle: LocalizedText?,
    val entitlement: EntitlementDto,
    val startingTrial: Boolean = false,
    val error: Boolean = false,
) {
    /** B02 shows the trial offer only while the server says the trial is available (decided on the device). */
    val offerTrial: Boolean get() = entitlement.trialAvailable && !entitlement.isPro
    val trialEnds: LocalDate?
        get() = entitlement.expiresAt?.let {
            runCatching { Instant.parse(it).toLocalDateTime(TimeZone.of("Asia/Kolkata")).date }.getOrNull()
        }
}

class AppliedMomentViewModel(matchId: String, matches: MatchRepository, private val accounts: AccountRepository) : ViewModel() {
    private val busy = MutableStateFlow(false to false)

    val state: StateFlow<AppliedMomentUi> = combine(matches.observeDetail(matchId), accounts.entitlement, busy) { d, e, (starting, error) ->
        AppliedMomentUi(d?.title, e, starting, error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppliedMomentUi(null, EntitlementDto.GUEST))

    /** B02 CTA: anonymous account if needed → `POST /v1/me/trial` → entitlement cached. */
    fun startTrial() {
        viewModelScope.launch {
            busy.value = true to false
            val ok = accounts.startTrial().isSuccess
            busy.value = false to !ok
        }
    }
}

@Composable
fun AppliedMomentRoute(
    matchId: String,
    onClose: () -> Unit,
    viewModel: AppliedMomentViewModel = koinViewModel(key = "applied_$matchId") { parametersOf(matchId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppliedMomentScreen(state, onStartTrial = viewModel::startTrial, onClose = onClose)
}

/** B02: Applied ✓ celebration + opt-in Pro trial (the peak moment), as a full-height sheet. */
@Composable
fun AppliedMomentScreen(state: AppliedMomentUi, onStartTrial: () -> Unit, onClose: () -> Unit) {
    val colors = SmTheme.colors
    val language = currentAppLanguage()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().testTag("B02"),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { SmIcon(SmIcons.Cancel) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Medallion()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.am_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            state.jobTitle?.let {
                Text(
                    stringResource(Res.string.am_subtitle, it.pick(language)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            when {
                state.entitlement.isPro -> TrialOnCard(state)
                state.offerTrial -> TrialOfferCard(state, onStartTrial, onClose)
            }
        }
        if (!state.offerTrial) {
            SmButton(
                stringResource(Res.string.am_done),
                onClose,
                Modifier.fillMaxWidth().padding(16.dp).testTag("btn_done"),
                icon = SmIcons.Check,
            )
        }
    }
}

@Composable
private fun Medallion() {
    val colors = SmTheme.colors
    Box(Modifier.size(150.dp, 110.dp), contentAlignment = Alignment.Center) {
        // Marigold confetti (static), then the green medallion.
        val bits = listOf(-62 to -30, 58 to -36, -48 to 30, 66 to 20, -18 to -46, 30 to 44, -70 to 4, 40 to -6)
        bits.forEachIndexed { i, (x, y) ->
            Box(
                Modifier.offset(x.dp, y.dp).size(if (i % 2 == 0) 8.dp else 6.dp, 4.dp).rotate((i * 37).toFloat())
                    .background(if (i % 3 == 0) colors.today else colors.marigold, RoundedCornerShape(2.dp)),
            )
        }
        Box(Modifier.size(84.dp).background(colors.successContainer, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                SmIcon(SmIcons.Check, tint = MaterialTheme.colorScheme.onPrimary, size = 36.dp)
            }
        }
    }
}

@Composable
private fun TrialOfferCard(state: AppliedMomentUi, onStartTrial: () -> Unit, onNotNow: () -> Unit) {
    val colors = SmTheme.colors
    val shape = RoundedCornerShape(SmRadius.xl)
    Column(
        Modifier.fillMaxWidth()
            .border(1.5.dp, colors.marigold, shape)
            .background(colors.marigoldContainer.copy(alpha = 0.45f), shape)
            .padding(16.dp)
            .testTag("trial_offer"),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge("Pro", colors.marigold, colors.onMarigold, SmIcons.Pro)
            Badge(stringResource(Res.string.am_trial_badge), colors.card, MaterialTheme.colorScheme.onSurface, null)
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(Res.string.am_trial_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(Res.string.am_trial_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Step(SmIcons.Forms, Res.string.am_step_admit)
            Step(SmIcons.Event, Res.string.am_step_exam)
            Step(SmIcons.Lock, Res.string.am_step_answer)
            Step(SmIcons.Pro, Res.string.am_step_result)
            Step(SmIcons.TaskAlt, Res.string.am_step_dv)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SmIcon(SmIcons.Cancel, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp)
            Text(
                stringResource(Res.string.am_no_ads),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        SmButton(
            stringResource(Res.string.am_start_trial),
            onStartTrial,
            Modifier.fillMaxWidth().testTag("btn_start_trial"),
            enabled = !state.startingTrial,
        )
        if (state.error) {
            Text(
                stringResource(Res.string.am_trial_error),
                style = MaterialTheme.typography.bodySmall,
                color = colors.today,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            stringResource(Res.string.am_fineprint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        TextButton(onClick = onNotNow, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(
                stringResource(Res.string.am_not_now),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TrialOnCard(state: AppliedMomentUi) {
    val colors = SmTheme.colors
    val language = currentAppLanguage()
    val shape = RoundedCornerShape(SmRadius.xl)
    Column(
        Modifier.fillMaxWidth().background(colors.successContainer, shape).padding(16.dp).testTag("trial_on"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmIcon(SmIcons.Pro, tint = colors.onSuccessContainer, size = 22.dp)
            Text(
                stringResource(Res.string.am_trial_on, state.trialEnds?.let { SmFormat.longDate(it, language) }.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSuccessContainer,
            )
        }
        Text(stringResource(Res.string.am_trial_on_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSuccessContainer)
    }
}

@Composable
private fun Badge(text: String, container: Color, content: Color, icon: DrawableResource?) {
    Row(
        Modifier.background(container, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { SmIcon(it, tint = content, size = 14.dp) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
private fun Step(icon: DrawableResource, label: StringResource) {
    val colors = SmTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(34.dp).border(1.dp, colors.marigold, CircleShape).background(colors.card, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SmIcon(icon, tint = colors.marigoldIcon, size = 18.dp)
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
