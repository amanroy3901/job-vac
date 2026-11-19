package com.sarkarimatch.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sarkarimatch.core.designsystem.R
import com.sarkarimatch.core.designsystem.theme.SmRadius
import com.sarkarimatch.core.designsystem.theme.SmTheme

/**
 * Stand-in for a screen that is not built yet. Shows the inventory ID (docs/design/01-screen-inventory.md) so a
 * tester can see where they are; the test tag is the screen ID.
 */
@Composable
fun PlaceholderScreen(
    screenId: String,
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    applyInsets: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (applyInsets) Modifier.safeDrawingPadding() else Modifier)
            .padding(horizontal = 16.dp)
            .testTag(screenId),
    ) {
        Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    SmIcon(SmIcons.ArrowBack, contentDescription = stringResource(R.string.ds_back))
                }
            }
        }
        Box(
            Modifier
                .size(56.dp)
                .background(SmTheme.colors.marigoldContainer, RoundedCornerShape(SmRadius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            Text(screenId, style = MaterialTheme.typography.titleLarge, color = SmTheme.colors.marigoldIcon)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ds_placeholder_body, screenId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (primaryLabel != null && onPrimary != null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmButton(primaryLabel, onPrimary, Modifier.fillMaxWidth(), icon = SmIcons.ArrowForward)
            }
        }
    }
}

/** "Continue" in the app language, for placeholder flows. */
@Composable
fun continueLabel(): String = stringResource(R.string.ds_continue)
