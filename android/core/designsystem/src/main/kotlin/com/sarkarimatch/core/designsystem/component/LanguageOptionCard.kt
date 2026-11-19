package com.sarkarimatch.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sarkarimatch.core.designsystem.theme.SmRadius
import com.sarkarimatch.core.designsystem.theme.SmTheme

/** S02/S40 option: the language's own name, large, in its own script, plus a helper line. */
@Composable
fun LanguageOptionCard(
    endonym: String,
    helper: String,
    badge: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(SmRadius.xl)
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else SmTheme.colors.card)
            .border(BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) scheme.primary else SmTheme.colors.hairline), shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(if (selected) scheme.primary else SmTheme.colors.marigoldContainer, RoundedCornerShape(SmRadius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                badge,
                style = MaterialTheme.typography.titleLarge,
                color = if (selected) scheme.onPrimary else SmTheme.colors.marigoldIcon,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(endonym, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
            Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .size(24.dp)
                .border(2.dp, if (selected) scheme.primary else SmTheme.colors.hairline, CircleShape)
                .padding(5.dp)
                .background(if (selected) scheme.primary else SmTheme.colors.card, CircleShape),
        )
    }
}
