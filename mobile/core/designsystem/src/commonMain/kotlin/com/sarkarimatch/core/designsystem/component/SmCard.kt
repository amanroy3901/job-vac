package com.sarkarimatch.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.sarkarimatch.core.designsystem.theme.SmRadius
import com.sarkarimatch.core.designsystem.theme.SmTheme

/**
 * White card, radius 24, soft brown-tinted shadow (tokens shadow.card composeFallback: elevation 4, spot
 * shadowTint @ 0.20, ambient @ 0.08). No border.
 */
@Composable
fun SmCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(SmRadius.xl)
    val tint = SmTheme.colors.shadowTint
    Column(
        modifier = modifier
            .shadow(4.dp, shape, ambientColor = tint.copy(alpha = 0.08f), spotColor = tint.copy(alpha = 0.20f))
            .clip(shape)
            .background(SmTheme.colors.card),
        content = content,
    )
}
