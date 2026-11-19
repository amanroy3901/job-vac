package com.sarkarimatch.feature.onboarding.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarkarimatch.app.resources.Res
import com.sarkarimatch.app.resources.dob_age_today
import com.sarkarimatch.app.resources.dob_age_value
import com.sarkarimatch.app.resources.dob_day
import com.sarkarimatch.app.resources.dob_month
import com.sarkarimatch.app.resources.dob_private
import com.sarkarimatch.app.resources.dob_step
import com.sarkarimatch.app.resources.dob_subtitle
import com.sarkarimatch.app.resources.dob_title
import com.sarkarimatch.app.resources.dob_warning
import com.sarkarimatch.app.resources.dob_year
import com.sarkarimatch.core.data.profile.LocalProfile
import com.sarkarimatch.core.data.profile.ProfileDraft
import com.sarkarimatch.core.designsystem.component.SmButton
import com.sarkarimatch.core.designsystem.component.SmIcon
import com.sarkarimatch.core.designsystem.component.SmIcons
import com.sarkarimatch.core.designsystem.component.continueLabel
import com.sarkarimatch.core.designsystem.theme.SmRadius
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.designsystem.theme.tabular
import com.sarkarimatch.core.model.Ist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.monthsUntil
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** S06 state: three digit fields; [dob] is set once they form a real date. */
data class DobUiState(val day: String = "", val month: String = "", val year: String = "", val today: LocalDate = Ist.today()) {
    val dob: LocalDate? get() = runCatching { LocalDate(
        year.toInt(),
        month.toInt(),
        day.toInt(),
    ) }.getOrNull()?.takeIf { year.length == 4 && it < today }

    /** Whole years and remaining months today (IST), for "आपकी उम्र आज 23 साल 6 महीने". */
    val age: Pair<Int, Int>? get() = dob?.let { val m = it.monthsUntil(today); m / 12 to m % 12 }
}

class DobViewModel(private val draft: ProfileDraft) : ViewModel() {
    private val _state = MutableStateFlow(
        draft.state.value?.dob?.let { DobUiState(it.day.pad(), it.month.ordinal.plus(1).pad(), it.year.toString()) } ?: DobUiState(),
    )
    val state: StateFlow<DobUiState> = _state

    fun onDay(v: String) = _state.update { it.copy(day = v.digits(2)) }
    fun onMonth(v: String) = _state.update { it.copy(month = v.digits(2)) }
    fun onYear(v: String) = _state.update { it.copy(year = v.digits(4)) }

    fun onContinue(next: () -> Unit) {
        val dob = _state.value.dob ?: return
        draft.update { it?.copy(dob = dob) ?: LocalProfile(dob = dob) }
        next()
    }

    private companion object {
        fun String.digits(max: Int) = filter(Char::isDigit).take(max)
        fun Int.pad() = toString().padStart(2, '0')
    }
}

@Composable
fun DobRoute(onBack: () -> Unit, onNext: () -> Unit, viewModel: DobViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DobScreen(state, viewModel::onDay, viewModel::onMonth, viewModel::onYear, onBack, onContinue = { viewModel.onContinue(onNext) })
}

/** S06 Date of birth (onboarding step 3 / 6). */
@Composable
fun DobScreen(
    state: DobUiState,
    onDay: (String) -> Unit,
    onMonth: (String) -> Unit,
    onYear: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = SmTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("S06"),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { SmIcon(SmIcons.ArrowBack) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(Res.string.dob_step, STEP, STEPS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { STEP / STEPS.toFloat() },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = colors.sunken,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.dob_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(Res.string.dob_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DigitBox(stringResource(Res.string.dob_day), state.day, onDay, "DD", Modifier.weight(1f))
                DigitBox(stringResource(Res.string.dob_month), state.month, onMonth, "MM", Modifier.weight(1f))
                DigitBox(stringResource(Res.string.dob_year), state.year, onYear, "YYYY", Modifier.weight(1.5f))
            }
            Spacer(Modifier.height(16.dp))
            state.age?.let { (years, months) ->
                Row(
                    Modifier.fillMaxWidth().background(colors.successContainer, RoundedCornerShape(SmRadius.lg)).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).background(colors.card, CircleShape), contentAlignment = Alignment.Center) {
                        SmIcon(SmIcons.Event, tint = colors.onSuccessContainer, size = 20.dp)
                    }
                    Column {
                        Text(
                            stringResource(Res.string.dob_age_today),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSuccessContainer,
                        )
                        Text(
                            stringResource(Res.string.dob_age_value, years, months),
                            style = MaterialTheme.typography.titleLarge.tabular(),
                            color = colors.onSuccessContainer,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Note(SmIcons.Info, stringResource(Res.string.dob_warning), colors.warningContainer, colors.onWarningContainer)
            Spacer(Modifier.height(8.dp))
            Note(SmIcons.Lock, stringResource(Res.string.dob_private), colors.sunken, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SmButton(
            continueLabel(),
            onContinue,
            Modifier.fillMaxWidth().padding(16.dp).testTag("btn_continue"),
            icon = SmIcons.ArrowForward,
            enabled = state.dob != null,
        )
    }
}

@Composable
private fun DigitBox(label: String, value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier) {
    val colors = SmTheme.colors
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        val shape = RoundedCornerShape(SmRadius.md)
        Box(
            Modifier.fillMaxWidth().height(56.dp)
                .border(
                    if (value.isNotEmpty()) 2.dp else 1.dp,
                    if (value.isNotEmpty()) MaterialTheme.colorScheme.primary else colors.hairline,
                    shape,
                )
                .background(colors.card, shape),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.tabular().copy(
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.hairline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun Note(
    icon: org.jetbrains.compose.resources.DrawableResource,
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Row(
        Modifier.fillMaxWidth().background(container, RoundedCornerShape(SmRadius.md)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SmIcon(icon, tint = content, size = 18.dp, modifier = Modifier.padding(top = 2.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = content)
    }
}

private const val STEP = 3
private const val STEPS = 6
