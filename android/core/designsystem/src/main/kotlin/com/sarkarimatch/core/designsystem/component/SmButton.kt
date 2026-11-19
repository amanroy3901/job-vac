package com.sarkarimatch.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sarkarimatch.core.designsystem.theme.SmSize
import com.sarkarimatch.core.designsystem.theme.SmTheme

enum class SmButtonStyle { Primary, Tonal, Outline, Marigold }

/** Pill button, 52 dp (44 dp compact), icon + label; the label wraps to 2 lines instead of truncating. */
@Composable
fun SmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SmButtonStyle = SmButtonStyle.Primary,
    @DrawableRes icon: Int? = null,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val ext = SmTheme.colors
    val (container, content) = when (style) {
        SmButtonStyle.Primary -> scheme.primary to scheme.onPrimary
        SmButtonStyle.Tonal -> ext.sunken to scheme.onSurface
        SmButtonStyle.Outline -> ext.card to scheme.onSurface
        SmButtonStyle.Marigold -> ext.marigold.container to ext.marigold.content
    }
    val shadow = when (style) {
        SmButtonStyle.Primary -> Modifier.shadow(6.dp, CircleShape, spotColor = scheme.primary.copy(alpha = 0.5f))
        SmButtonStyle.Marigold -> Modifier.shadow(6.dp, CircleShape, spotColor = ext.marigold.container)
        else -> Modifier
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        border = if (style == SmButtonStyle.Outline) BorderStroke(1.5.dp, ext.hairline) else null,
        contentPadding = PaddingValues(horizontal = if (compact) 16.dp else 20.dp, vertical = 8.dp),
        modifier = modifier
            .then(if (enabled) shadow else Modifier)
            .heightIn(min = if (compact) SmSize.buttonHeightCompact else SmSize.buttonHeight),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) SmIcon(icon, size = if (compact) SmSize.iconMd else 22.dp)
            Text(
                text = text,
                style = if (compact) MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize.times(15f / 14f))
                else MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
