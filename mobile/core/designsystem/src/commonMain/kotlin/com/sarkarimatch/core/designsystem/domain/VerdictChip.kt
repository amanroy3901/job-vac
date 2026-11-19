package com.sarkarimatch.core.designsystem.domain

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sarkarimatch.core.designsystem.R
import com.sarkarimatch.core.designsystem.component.SmChip
import com.sarkarimatch.core.designsystem.component.SmIcons
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.designsystem.theme.TonePair
import com.sarkarimatch.core.model.Verdict

/** ✓ Eligible (forest, tinted) / ? Check yourself (ochre). Icon + word + colour, never colour alone. */
@Composable
fun VerdictChip(verdict: Verdict, modifier: Modifier = Modifier) {
    val colors = SmTheme.colors
    val (icon, label, tone) = when (verdict) {
        Verdict.ELIGIBLE ->
            Triple(SmIcons.CheckCircle, R.string.ds_verdict_eligible, TonePair(colors.successContainer, colors.onSuccessContainer))
        Verdict.CHECK_YOURSELF ->
            Triple(SmIcons.Help, R.string.ds_verdict_check, TonePair(colors.warningContainer, colors.onWarningContainer))
        Verdict.NOT_ELIGIBLE ->
            Triple(SmIcons.Cancel, R.string.ds_verdict_not_eligible, TonePair(colors.sunken, MaterialTheme.colorScheme.onSurfaceVariant))
    }
    SmChip(text = stringResource(label), icon = icon, colors = tone, modifier = modifier)
}
