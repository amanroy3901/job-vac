package com.sarkarimatch.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarkarimatch.core.designsystem.component.LanguageOptionCard
import com.sarkarimatch.core.designsystem.component.SmButton
import com.sarkarimatch.core.designsystem.component.SmIcon
import com.sarkarimatch.core.designsystem.component.SmIcons
import com.sarkarimatch.core.designsystem.theme.SmRadius
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.model.AppLanguage
import com.sarkarimatch.feature.onboarding.R

@Composable
fun LanguageRoute(
    onContinue: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LanguageScreen(
        state = state,
        onSelect = viewModel::select,
        onContinue = { viewModel.confirm(onContinue) },
        onBack = onBack,
    )
}

/** S02 — language picker, Direction C. Each option is labelled in its own script. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageScreen(
    state: LanguageUiState,
    onSelect: (AppLanguage) -> Unit,
    onContinue: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val colors = SmTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .testTag("S02"),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { SmIcon(SmIcons.ArrowBack) }
            } else {
                Spacer(Modifier.height(24.dp))
            }
            // Greeting card: the one marigold surface on this screen.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(colors.marigoldContainer, colors.card)),
                        RoundedCornerShape(SmRadius.xl),
                    )
                    .padding(20.dp),
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(colors.marigold, RoundedCornerShape(SmRadius.lg)),
                    contentAlignment = Alignment.Center,
                ) {
                    SmIcon(SmIcons.Translate, tint = colors.onMarigold, size = 30.dp)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.onb_language_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                // Bilingual hint so a user who cannot read the current language still finds the picker.
                Text(
                    text = "Choose your language · अपनी भाषा चुनें",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.onb_language_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Column(Modifier.semantics { selectableGroup() }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.enabled.forEach { language ->
                    LanguageOptionCard(
                        endonym = language.endonym,
                        helper = stringResource(
                            if (language == AppLanguage.HI) R.string.onb_language_helper_hi else R.string.onb_language_helper_en,
                        ),
                        badge = if (language == AppLanguage.HI) "अ" else "A",
                        selected = state.selected == language,
                        onClick = { onSelect(language) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.onb_language_coming_soon),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.comingSoon.forEach { language ->
                    Text(
                        language.endonym,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(colors.sunken, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        SmButton(
            text = stringResource(R.string.onb_language_continue),
            onClick = onContinue,
            icon = SmIcons.ArrowForward,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Preview(locale = "hi")
@Composable
private fun LanguageScreenPreview() {
    SmTheme(darkTheme = false) { LanguageScreen(LanguageUiState(AppLanguage.HI), {}, {}) }
}
