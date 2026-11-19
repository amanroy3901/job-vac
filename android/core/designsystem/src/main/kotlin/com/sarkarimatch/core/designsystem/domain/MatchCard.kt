package com.sarkarimatch.core.designsystem.domain

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sarkarimatch.core.designsystem.R
import com.sarkarimatch.core.designsystem.component.CategoryTile
import com.sarkarimatch.core.designsystem.component.DeadlineRibbon
import com.sarkarimatch.core.designsystem.component.FactChip
import com.sarkarimatch.core.designsystem.component.SmButton
import com.sarkarimatch.core.designsystem.component.SmButtonStyle
import com.sarkarimatch.core.designsystem.component.SmCard
import com.sarkarimatch.core.designsystem.component.SmChip
import com.sarkarimatch.core.designsystem.component.SmIcon
import com.sarkarimatch.core.designsystem.component.SmIcons
import com.sarkarimatch.core.designsystem.component.currentAppLanguage
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.designsystem.theme.TonePair
import com.sarkarimatch.core.model.AppLanguage
import com.sarkarimatch.core.model.DeadlineTone
import com.sarkarimatch.core.model.Ist
import com.sarkarimatch.core.model.MatchCardUi
import com.sarkarimatch.core.model.SmFormat
import com.sarkarimatch.core.model.Verdict
import com.sarkarimatch.core.model.deadlineTone
import java.time.LocalDate
import java.time.LocalTime

/**
 * S20 feed card (Direction C): deadline ribbon on Today/Urgent cards, category tile, title + organisation, fact
 * chips (fee, qualification), first "why you match" line, and the two primary actions. One TalkBack stop with
 * custom actions (design 06).
 */
@Composable
fun MatchCard(
    card: MatchCardUi,
    today: LocalDate,
    onOpen: () -> Unit,
    onApplied: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = currentAppLanguage()
    val daysLeft = Ist.daysLeft(today, card.lastDate)
    val tone = deadlineTone(daysLeft)
    val appliedLabel = stringResource(R.string.ds_action_applied)
    val laterLabel = stringResource(R.string.ds_action_later)

    SmCard(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .testTag("match_card_${card.matchId}")
            .clickable(onClick = onOpen)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(appliedLabel) {
                        onApplied()
                        true
                    },
                    CustomAccessibilityAction(laterLabel) {
                        onLater()
                        true
                    },
                )
            },
    ) {
        Ribbon(card, tone, daysLeft, language)
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                CategoryTile(card.tile)
                Column(Modifier.weight(1f)) {
                    Text(card.title.pick(language), style = MaterialTheme.typography.titleMedium, maxLines = 3)
                    val org = card.organisation.pick(language)
                    Text(
                        text = card.vacancies?.let { stringResource(R.string.ds_vacancies, org, SmFormat.count(it)) } ?: org,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FactChip(
                    text = card.feePaise?.let { if (it == 0L) stringResource(R.string.ds_fee_free) else SmFormat.amount(it) } ?: "—",
                    icon = SmIcons.Rupee,
                )
                FactChip(text = card.qualification.pick(language), icon = SmIcons.School, weight = 1.6f)
            }
            if (tone == DeadlineTone.Calm || tone == DeadlineTone.Soon) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    VerdictChip(card.verdict)
                    SmChip(
                        text = pluralStringResource(R.plurals.ds_days_left, daysLeft.toInt(), daysLeft.toInt()),
                        icon = SmIcons.Event,
                        colors = TonePair(SmTheme.colors.sunken, MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
            card.whyLine?.let { why ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    val ok = card.verdict == Verdict.ELIGIBLE
                    SmIcon(
                        if (ok) SmIcons.CheckCircle else SmIcons.Help,
                        tint = if (ok) MaterialTheme.colorScheme.primary else SmTheme.colors.onWarningContainer,
                        size = 20.dp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(why.pick(language), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmButton(appliedLabel, onApplied, Modifier.weight(1f), SmButtonStyle.Primary, SmIcons.TaskAlt, compact = true)
                SmButton(laterLabel, onLater, style = SmButtonStyle.Tonal, icon = SmIcons.Snooze, compact = true)
            }
        }
    }
}

@Composable
private fun Ribbon(card: MatchCardUi, tone: DeadlineTone, daysLeft: Long, language: AppLanguage) {
    val colors = SmTheme.colors
    when (tone) {
        DeadlineTone.Today -> {
            val text = card.lastTime?.let { stringResource(R.string.ds_ribbon_today_until, hourLabel(LocalTime.parse(it))) }
                ?: stringResource(R.string.ds_ribbon_today)
            DeadlineRibbon(text, SmIcons.Alarm, TonePair(colors.today, colors.onToday))
        }
        DeadlineTone.Urgent -> DeadlineRibbon(
            text = pluralStringResource(
                R.plurals.ds_ribbon_days_left,
                daysLeft.toInt(),
                daysLeft.toInt(),
                SmFormat.shortDate(card.lastDate, language),
            ),
            icon = SmIcons.Timer,
            colors = TonePair(colors.urgentContainer, colors.onUrgentContainer),
        )
        else -> Unit
    }
}

@Composable
private fun hourLabel(time: LocalTime): String =
    if (time.hour < 12) {
        stringResource(R.string.ds_hour_am, SmFormat.hour12(time))
    } else {
        stringResource(R.string.ds_hour_pm, SmFormat.hour12(time))
    }
