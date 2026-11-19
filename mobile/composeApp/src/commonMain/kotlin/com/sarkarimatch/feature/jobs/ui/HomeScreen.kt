package com.sarkarimatch.feature.jobs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.jobs_app_name
import com.sarkarimatch.app.resources.jobs_disclaimer
import com.sarkarimatch.app.resources.jobs_greeting
import com.sarkarimatch.app.resources.jobs_greeting_subtitle
import com.sarkarimatch.app.resources.jobs_notifications
import com.sarkarimatch.app.resources.jobs_pill_later
import com.sarkarimatch.app.resources.jobs_pill_three_days
import com.sarkarimatch.app.resources.jobs_pill_today
import com.sarkarimatch.app.resources.jobs_section_later
import com.sarkarimatch.app.resources.jobs_section_three_days
import com.sarkarimatch.app.resources.jobs_section_today
import com.sarkarimatch.app.resources.jobs_waiting_for_you
import com.sarkarimatch.core.designsystem.component.SectionHeader
import com.sarkarimatch.core.designsystem.component.SmIcon
import com.sarkarimatch.core.designsystem.component.SmIcons
import com.sarkarimatch.core.designsystem.component.currentAppLanguage
import com.sarkarimatch.core.designsystem.domain.MatchCard
import com.sarkarimatch.core.designsystem.theme.SmRadius
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.designsystem.theme.tabular
import com.sarkarimatch.core.model.MatchCardUi
import com.sarkarimatch.core.model.PaywallSource
import com.sarkarimatch.core.model.SmFormat
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoute(
    onOpenJob: (String) -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onOpenDeliveryHealth: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onOpenJob = onOpenJob,
        // B01 (confirm applied) → B02 paywall moment arrive with FE-18 / FE-24.
        onApplied = { onOpenPaywall(PaywallSource.APPLIED_MOMENT) },
        onLater = {},
        onOpenAlerts = onOpenDeliveryHealth,
    )
}

/** S20 Home — Direction C "Marigold Home". */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenJob: (String) -> Unit,
    onApplied: (String) -> Unit,
    onLater: (String) -> Unit,
    onOpenAlerts: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("S20"),
    ) {
        item(key = "top") { TopBar(onOpenAlerts, hasUnread = state.dueToday.isNotEmpty()) }
        item(key = "greeting") { GreetingCard(state) }
        section("today", Res.string.jobs_section_today, SmIcons.Alarm, state.dueToday, state, onOpenJob, onApplied, onLater, urgent = true)
        section("three", Res.string.jobs_section_three_days, SmIcons.Timer, state.withinThreeDays, state, onOpenJob, onApplied, onLater)
        section("later", Res.string.jobs_section_later, SmIcons.Event, state.later, state, onOpenJob, onApplied, onLater)
        item(key = "disclaimer") {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmIcon(
                    SmIcons.Info,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 18.dp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    stringResource(Res.string.jobs_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.section(
    key: String,
    title: StringResource,
    icon: DrawableResource,
    cards: List<MatchCardUi>,
    state: HomeUiState,
    onOpenJob: (String) -> Unit,
    onApplied: (String) -> Unit,
    onLater: (String) -> Unit,
    urgent: Boolean = false,
) {
    if (cards.isEmpty()) return
    item(key = "header_$key") {
        SectionHeader(
            title = stringResource(title),
            icon = icon,
            count = cards.size,
            tint = if (urgent) SmTheme.colors.today else MaterialTheme.colorScheme.onSurface,
        )
    }
    items(cards, key = { it.matchId }) { card ->
        MatchCard(
            card = card,
            today = state.today,
            onOpen = { onOpenJob(card.matchId) },
            onApplied = { onApplied(card.matchId) },
            onLater = { onLater(card.matchId) },
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun TopBar(onOpenAlerts: () -> Unit, hasUnread: Boolean) {
    Row(
        Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Wordmark: marigold tile + name (treated as a logo; not font-scaled in a later pass, design 02 §3.2).
        Box(
            Modifier
                .size(28.dp)
                .background(SmTheme.colors.marigold, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            SmIcon(SmIcons.BellActive, tint = SmTheme.colors.onMarigold, size = 18.dp)
        }
        Text(
            stringResource(Res.string.jobs_app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .semantics { heading() },
        )
        Box {
            IconButton(onClick = onOpenAlerts) {
                SmIcon(SmIcons.Bell, contentDescription = stringResource(Res.string.jobs_notifications))
            }
            if (hasUnread) {
                Box(
                    Modifier
                        .padding(top = 12.dp, end = 12.dp)
                        .size(9.dp)
                        .background(SmTheme.colors.today, CircleShape)
                        .align(Alignment.TopEnd),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GreetingCard(state: HomeUiState) {
    val colors = SmTheme.colors
    val language = currentAppLanguage()
    val shape = RoundedCornerShape(SmRadius.xl)
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = colors.shadowTint.copy(alpha = 0.08f),
                spotColor = colors.shadowTint.copy(alpha = 0.2f),
            )
            .background(Brush.linearGradient(0f to colors.marigoldContainer, 0.7f to colors.card), shape)
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(stringResource(Res.string.jobs_greeting), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(
                Res.string.jobs_greeting_subtitle,
                SmFormat.shortDate(state.today, language),
                pluralStringResource(Res.plurals.jobs_waiting_for_you, state.waitingCount, state.waitingCount),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.dueToday.isNotEmpty()) {
                Pill(stringResource(Res.string.jobs_pill_today, state.dueToday.size), SmIcons.Alarm, colors.today, colors.onToday)
            }
            if (state.withinThreeDays.isNotEmpty()) {
                Pill(
                    stringResource(Res.string.jobs_pill_three_days, state.withinThreeDays.size),
                    SmIcons.Timer,
                    colors.urgentContainer,
                    colors.onUrgentContainer,
                )
            }
            if (state.later.isNotEmpty()) {
                Pill(
                    stringResource(Res.string.jobs_pill_later, state.later.size),
                    SmIcons.Event,
                    colors.card,
                    MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, icon: DrawableResource, container: Color, content: Color) {
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .background(container, CircleShape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SmIcon(icon, tint = content, size = 18.dp)
        Text(text, style = MaterialTheme.typography.labelMedium.tabular(), color = content)
    }
}
