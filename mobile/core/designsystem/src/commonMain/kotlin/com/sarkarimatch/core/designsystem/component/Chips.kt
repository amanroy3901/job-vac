package com.sarkarimatch.core.designsystem.component

import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sarkarimatch.core.designsystem.theme.SmRadius
import com.sarkarimatch.core.designsystem.theme.SmSize
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.designsystem.theme.TonePair
import com.sarkarimatch.core.designsystem.theme.tabular
import com.sarkarimatch.core.model.ExamCategoryTile

/** Tinted pill with icon + word (status is never colour alone). */
@Composable
fun SmChip(
    text: String,
    icon: DrawableResource?,
    colors: TonePair,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = SmSize.verdictChipHeight)
            .background(colors.container, CircleShape)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) SmIcon(icon, tint = colors.content, size = SmSize.iconSm)
        Text(text, style = MaterialTheme.typography.labelMedium.tabular(), color = colors.content, maxLines = 1)
    }
}

/** Full-width deadline ribbon at the top of an urgent card ("Last day today · until 11 pm"). */
@Composable
fun DeadlineRibbon(text: String, icon: DrawableResource, colors: TonePair, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SmSize.ribbonMinHeight)
            .background(colors.container)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SmIcon(icon, tint = colors.content, size = SmSize.iconMd)
        Text(text, style = MaterialTheme.typography.labelMedium.tabular(), color = colors.content)
    }
}

/** Fact chip on the sunken background (fee, qualification). */
@Composable
fun RowScope.FactChip(text: String, icon: DrawableResource, weight: Float = 1f) {
    Row(
        modifier = Modifier
            .weight(weight)
            .defaultMinSize(minHeight = SmSize.factChipMinHeight)
            .background(SmTheme.colors.sunken, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SmIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = SmSize.iconSm)
        Text(text, style = MaterialTheme.typography.labelMedium.tabular(), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** 48 dp category icon tile (police, SSC, railway, bank, state): recognisable without reading. */
@Composable
fun CategoryTile(tile: ExamCategoryTile, modifier: Modifier = Modifier) {
    val colors = SmTheme.colors.category(tile)
    val icon = when (tile) {
        ExamCategoryTile.Police -> SmIcons.Police
        ExamCategoryTile.Ssc -> SmIcons.Ssc
        ExamCategoryTile.Railway -> SmIcons.Railway
        ExamCategoryTile.Bank -> SmIcons.Bank
        ExamCategoryTile.State -> SmIcons.State
    }
    Box(
        modifier = modifier
            .size(SmSize.categoryTile)
            .background(colors.container, RoundedCornerShape(SmRadius.lg)),
        contentAlignment = Alignment.Center,
    ) {
        SmIcon(icon, tint = colors.icon)
    }
}

/** Feed section header: icon + title + count bubble. */
@Composable
fun SectionHeader(
    title: String,
    icon: DrawableResource,
    count: Int?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SmIcon(icon, tint = tint, size = 22.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, color = tint, modifier = Modifier.weight(1f))
        if (count != null) {
            Box(
                Modifier
                    .defaultMinSize(minWidth = SmSize.countBubble, minHeight = SmSize.countBubble)
                    .background(SmTheme.colors.sunken, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}
