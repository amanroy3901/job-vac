package com.sarkarimatch.feature.applications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.apps_applied
import com.sarkarimatch.app.resources.mf_applied_on
import com.sarkarimatch.app.resources.mf_empty
import com.sarkarimatch.app.resources.mf_next
import com.sarkarimatch.app.resources.mf_pro_off
import com.sarkarimatch.app.resources.mf_pro_on
import com.sarkarimatch.app.resources.mf_subtitle
import com.sarkarimatch.core.data.account.AccountRepository
import com.sarkarimatch.core.data.repository.AppliedFormUi
import com.sarkarimatch.core.data.repository.MatchRepository
import com.sarkarimatch.core.designsystem.component.CategoryTile
import com.sarkarimatch.feature.jobs.ui.PaddedCard
import com.sarkarimatch.core.designsystem.component.SmChip
import com.sarkarimatch.core.designsystem.component.SmIcon
import com.sarkarimatch.core.designsystem.component.SmIcons
import com.sarkarimatch.core.designsystem.component.currentAppLanguage
import com.sarkarimatch.core.designsystem.theme.SmRadius
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.designsystem.theme.TonePair
import com.sarkarimatch.core.model.SmFormat
import com.sarkarimatch.core.network.EntitlementDto
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

data class MyFormsUi(
    val forms: List<AppliedFormUi> = emptyList(),
    val entitlement: EntitlementDto = EntitlementDto.GUEST,
    val loading: Boolean = true,
)

class MyFormsViewModel(matches: MatchRepository, accounts: AccountRepository) : ViewModel() {
    val state: StateFlow<MyFormsUi> = combine(matches.observeApplied(), accounts.entitlement) { f, e -> MyFormsUi(f, e, loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyFormsUi())
}

@Composable
fun MyFormsRoute(onOpenJob: (String) -> Unit, viewModel: MyFormsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyFormsScreen(state, onOpenJob)
}

/** S22 "मेरे फ़ॉर्म": applications tracked on this phone. */
@Composable
fun MyFormsScreen(state: MyFormsUi, onOpenJob: (String) -> Unit) {
    val language = currentAppLanguage()
    val colors = SmTheme.colors
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("S22")) {
        item {
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
                Text(stringResource(Res.string.apps_applied), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(Res.string.mf_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            val e = state.entitlement
            val ends = e.expiresAt?.let { runCatching { Instant.parse(it).toLocalDateTime(TimeZone.of("Asia/Kolkata")).date }.getOrNull() }
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .background(if (e.isPro) colors.successContainer else colors.sunken, RoundedCornerShape(SmRadius.md))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmIcon(
                    if (e.isPro) SmIcons.Pro else SmIcons.Bell,
                    tint = if (e.isPro) colors.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 20.dp,
                )
                Text(
                    if (e.isPro) stringResource(
                        Res.string.mf_pro_on,
                        ends?.let { SmFormat.shortDate(it, language) }.orEmpty(),
                    ) else stringResource(Res.string.mf_pro_off),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (e.isPro) colors.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!state.loading && state.forms.isEmpty()) {
            item { Text(stringResource(Res.string.mf_empty), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge) }
        }
        items(state.forms, key = { it.applicationId }) { form ->
            PaddedCard(Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
            ).clickable { onOpenJob(form.matchId) }.testTag("form_${form.applicationId}")) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryTile(form.tile)
                    Column(Modifier.weight(1f)) {
                        Text(form.title.pick(language), style = MaterialTheme.typography.titleMedium)
                        Text(
                            form.organisation.pick(language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmChip(
                        stringResource(Res.string.mf_applied_on, SmFormat.shortDate(form.appliedOn, language)),
                        SmIcons.CheckCircle,
                        TonePair(colors.successContainer, colors.onSuccessContainer),
                    )
                    SmChip(
                        stringResource(Res.string.mf_next),
                        SmIcons.Event,
                        TonePair(colors.sunken, MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }
        item { Box(Modifier.height(24.dp)) }
    }
}
