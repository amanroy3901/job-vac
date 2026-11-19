package com.sarkarimatch.feature.jobs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.jd_applied
import com.sarkarimatch.app.resources.jd_applied_state
import com.sarkarimatch.app.resources.jd_apply_window
import com.sarkarimatch.app.resources.jd_fee
import com.sarkarimatch.app.resources.jd_fee_free
import com.sarkarimatch.app.resources.jd_later
import com.sarkarimatch.app.resources.jd_need_title
import com.sarkarimatch.app.resources.jd_not_a_match
import com.sarkarimatch.app.resources.jd_official_notice
import com.sarkarimatch.app.resources.jd_posts
import com.sarkarimatch.app.resources.jd_qualification
import com.sarkarimatch.app.resources.jd_stays_on_phone
import com.sarkarimatch.app.resources.jd_until
import com.sarkarimatch.app.resources.jd_why_title
import com.sarkarimatch.app.resources.jd_why_title_check
import com.sarkarimatch.app.resources.jd_why_title_not
import com.sarkarimatch.app.resources.jobs_disclaimer
import com.sarkarimatch.core.data.repository.JobDetailUi
import com.sarkarimatch.core.data.repository.MatchActionRepository
import com.sarkarimatch.core.data.repository.MatchRepository
import com.sarkarimatch.core.data.repository.UserAction
import com.sarkarimatch.core.designsystem.component.CategoryTile
import com.sarkarimatch.core.designsystem.component.SmButton
import com.sarkarimatch.core.designsystem.component.SmButtonStyle
import com.sarkarimatch.core.designsystem.component.SmCard
import com.sarkarimatch.core.designsystem.component.SmChip
import com.sarkarimatch.core.designsystem.component.SmIcon
import com.sarkarimatch.core.designsystem.component.SmIcons
import com.sarkarimatch.core.designsystem.component.currentAppLanguage
import com.sarkarimatch.core.designsystem.domain.VerdictChip
import com.sarkarimatch.core.designsystem.resources.ds_days_left
import com.sarkarimatch.core.designsystem.resources.ds_ribbon_today
import com.sarkarimatch.core.designsystem.theme.SmRadius
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.designsystem.theme.TonePair
import com.sarkarimatch.core.designsystem.theme.tabular
import com.sarkarimatch.core.eligibility.LineResult
import com.sarkarimatch.core.model.AppLanguage
import com.sarkarimatch.core.model.DeadlineTone
import com.sarkarimatch.core.model.Ist
import com.sarkarimatch.core.model.SmFormat
import com.sarkarimatch.core.model.Verdict
import com.sarkarimatch.core.model.deadlineTone
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.sarkarimatch.core.designsystem.resources.Res as DsRes

class JobDetailViewModel(
    private val matchId: String,
    matches: MatchRepository,
    private val actions: MatchActionRepository,
) : ViewModel() {
    val state: StateFlow<JobDetailUi?> = matches.observeDetail(matchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "फ़ॉर्म भर दिया": local write, then the B02 moment. */
    fun onApplied(then: () -> Unit) {
        viewModelScope.launch {
            if (state.value?.status != null && state.value?.status != "applied") actions.record(matchId, UserAction.Applied())
            then()
        }
    }
}

@Composable
fun JobDetailRoute(
    matchId: String,
    onBack: () -> Unit,
    onApplied: (String) -> Unit,
    viewModel: JobDetailViewModel = koinViewModel(key = matchId) { parametersOf(matchId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state?.let { detail ->
        JobDetailScreen(
            detail,
            today = Ist.today(),
            onBack = onBack,
            onApplied = { viewModel.onApplied { onApplied(matchId) } },
            onLater = onBack,
        )
    }
}

/** S21 job detail (Marigold Home): hero with tear-off calendar, why you match, what you need, sticky actions. */
@Composable
fun JobDetailScreen(detail: JobDetailUi, today: LocalDate, onBack: () -> Unit, onApplied: () -> Unit, onLater: () -> Unit) {
    val language = currentAppLanguage()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("S21")) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { SmIcon(SmIcons.ArrowBack) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hero(detail, today, language)
            WhyCard(detail)
            NeedCard(detail, language)
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmIcon(
                    SmIcons.Lock,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 16.dp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    stringResource(Res.string.jd_stays_on_phone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmIcon(
                    SmIcons.Info,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 16.dp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    stringResource(Res.string.jobs_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomActions(detail, onApplied, onLater)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(detail: JobDetailUi, today: LocalDate, language: AppLanguage) {
    val colors = SmTheme.colors
    val daysLeft = Ist.daysLeft(today, detail.lastDate)
    val tone = deadlineTone(daysLeft)
    PaddedCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryTile(detail.tile)
            Column(Modifier.weight(1f)) {
                Text(detail.title.pick(language), style = MaterialTheme.typography.titleLarge)
                val vac = detail.vacancies?.let { " · " + stringResource(Res.string.jd_posts, SmFormat.count(it)) }.orEmpty()
                Text(
                    detail.organisation.pick(language) + vac,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TearOffCalendar(detail.lastDate, language, tone)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VerdictChip(detail.verdict)
                    val (label, pair) = when (tone) {
                        DeadlineTone.Today -> stringResource(DsRes.string.ds_ribbon_today) to TonePair(colors.today, colors.onToday)
                        DeadlineTone.Urgent -> pluralStringResource(DsRes.plurals.ds_days_left, daysLeft.toInt(), daysLeft.toInt()) to
                            TonePair(colors.urgentContainer, colors.onUrgentContainer)
                        else -> pluralStringResource(
                            DsRes.plurals.ds_days_left,
                            daysLeft.toInt().coerceAtLeast(0),
                            daysLeft.toInt().coerceAtLeast(0),
                        ) to
                            TonePair(colors.sunken, MaterialTheme.colorScheme.onSurface)
                    }
                    SmChip(label, SmIcons.Timer, pair)
                }
                detail.lastTime?.let { Text(
                    stringResource(Res.string.jd_until, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ) }
            }
        }
        detail.applyStart?.let { start ->
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth()
                    .background(colors.marigoldContainer, RoundedCornerShape(SmRadius.md))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmIcon(SmIcons.Event, tint = colors.onMarigoldContainer, size = 18.dp)
                Text(
                    stringResource(
                        Res.string.jd_apply_window,
                        SmFormat.shortDate(start, language),
                        SmFormat.shortDate(detail.lastDate, language),
                    ),
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = colors.onMarigoldContainer,
                )
            }
        }
    }
}

/** Tear-off calendar leaf: month band on top (terracotta when urgent), big day number, weekday. */
@Composable
private fun TearOffCalendar(date: LocalDate, language: AppLanguage, tone: DeadlineTone) {
    val colors = SmTheme.colors
    val band = if (tone == DeadlineTone.Today || tone == DeadlineTone.Urgent) colors.today else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(SmRadius.md)
    Column(
        Modifier.width(64.dp).border(1.dp, colors.hairline, shape).background(colors.card, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(band, RoundedCornerShape(topStart = SmRadius.md, topEnd = SmRadius.md))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                SmFormat.shortDate(date, language).substringAfter(' '),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onToday,
            )
        }
        Text(date.day.toString(), style = MaterialTheme.typography.headlineMedium.tabular(), fontWeight = FontWeight.SemiBold)
        Text(
            date.year.toString(),
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun WhyCard(detail: JobDetailUi) {
    val language = currentAppLanguage()
    val title = when (detail.verdict) {
        Verdict.ELIGIBLE -> Res.string.jd_why_title
        Verdict.CHECK_YOURSELF -> Res.string.jd_why_title_check
        Verdict.NOT_ELIGIBLE -> Res.string.jd_why_title_not
    }
    PaddedCard(Modifier.testTag("why_card")) {
        CardTitle(stringResource(title), SmIcons.TaskAlt)
        if (detail.status == null) {
            Text(
                stringResource(Res.string.jd_not_a_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        detail.why.forEach { line ->
            Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineIcon(line.result)
                Text(line.text.pick(language), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LineIcon(result: LineResult) {
    val colors = SmTheme.colors
    val (icon, pair) = when (result) {
        LineResult.PASS -> SmIcons.Check to TonePair(colors.successContainer, colors.onSuccessContainer)
        LineResult.CHECK -> SmIcons.Help to TonePair(colors.warningContainer, colors.onWarningContainer)
        LineResult.FAIL -> SmIcons.Cancel to TonePair(colors.urgentContainer, colors.onUrgentContainer)
        LineResult.INFO -> SmIcons.Info to TonePair(colors.sunken, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Box(Modifier.size(22.dp).background(pair.container, CircleShape), contentAlignment = Alignment.Center) {
        SmIcon(icon, tint = pair.content, size = 15.dp)
    }
}

@Composable
private fun NeedCard(detail: JobDetailUi, language: AppLanguage) {
    val colors = SmTheme.colors
    PaddedCard {
        CardTitle(stringResource(Res.string.jd_need_title), SmIcons.Rupee)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.background(colors.sunken, RoundedCornerShape(SmRadius.md)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    stringResource(Res.string.jd_fee),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val fee = detail.feePaise
                Text(
                    if (fee == null || fee == 0L) "₹0" else SmFormat.rupees(fee),
                    style = MaterialTheme.typography.headlineSmall.tabular(),
                )
            }
            val note = detail.feeNote?.pick(language) ?: if (detail.feePaise == 0L) stringResource(Res.string.jd_fee_free) else null
            note?.let { Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            ) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.jd_qualification),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(detail.qualification.pick(language), style = MaterialTheme.typography.bodyMedium)
        detail.officialPdfUrl?.let { url ->
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().border(1.dp, colors.hairline, RoundedCornerShape(SmRadius.md)).clickable { }.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).background(colors.urgentContainer, RoundedCornerShape(SmRadius.sm)),
                    contentAlignment = Alignment.Center,
                ) {
                    SmIcon(SmIcons.Forms, tint = colors.onUrgentContainer, size = 20.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.jd_official_notice), style = MaterialTheme.typography.titleSmall)
                    Text(
                        url.removePrefix("https://").removePrefix("www."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SmIcon(SmIcons.ArrowForward, size = 20.dp)
            }
        }
    }
}

@Composable
internal fun CardTitle(text: String, icon: org.jetbrains.compose.resources.DrawableResource) {
    val colors = SmTheme.colors
    Row(
        Modifier.padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(colors.marigoldContainer, RoundedCornerShape(SmRadius.sm)),
            contentAlignment = Alignment.Center,
        ) {
            SmIcon(icon, tint = colors.marigoldIcon, size = 18.dp)
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun BottomActions(detail: JobDetailUi, onApplied: () -> Unit, onLater: () -> Unit) {
    val colors = SmTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.card).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (detail.status == "applied") {
            Row(
                Modifier.fillMaxWidth().background(colors.successContainer, RoundedCornerShape(SmRadius.md)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmIcon(SmIcons.CheckCircle, tint = colors.onSuccessContainer, size = 20.dp)
                Text(
                    stringResource(Res.string.jd_applied_state),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSuccessContainer,
                )
            }
        } else if (detail.status != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmButton(
                    stringResource(Res.string.jd_applied),
                    onApplied,
                    Modifier.weight(1.4f).testTag("btn_applied"),
                    icon = SmIcons.TaskAlt,
                )
                SmButton(
                    stringResource(Res.string.jd_later),
                    onLater,
                    Modifier.weight(1f),
                    style = SmButtonStyle.Outline,
                    icon = SmIcons.Snooze,
                )
            }
        }
    }
}

/** SmCard with the standard 16 dp card padding (SmCard itself has none; MatchCard pads its own sections). */
@Composable
internal fun PaddedCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    SmCard(modifier) { Column(Modifier.fillMaxWidth().padding(SmTheme.spacing.cardPadding), content = content) }
}
