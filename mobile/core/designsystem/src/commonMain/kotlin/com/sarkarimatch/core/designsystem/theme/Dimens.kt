package com.sarkarimatch.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** tokens.json `space.*` (8-pt grid). */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val x3l: Dp = 32.dp,
    val x4l: Dp = 40.dp,
    val x5l: Dp = 48.dp,
    val screenGutter: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    val listGap: Dp = 12.dp,
    val sectionGap: Dp = 20.dp,
    val cardGap: Dp = 12.dp,
)

internal val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** tokens.json `radius.*`: cards 24, tiles 16, buttons/chips pill. */
object SmRadius {
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 28.dp
}

internal val SmShapes = Shapes(
    extraSmall = RoundedCornerShape(SmRadius.sm),
    small = RoundedCornerShape(SmRadius.md),
    medium = RoundedCornerShape(SmRadius.lg),
    large = RoundedCornerShape(SmRadius.xl),
    extraLarge = RoundedCornerShape(SmRadius.xxl),
)

/** tokens.json `size.*`. */
object SmSize {
    val touchMin = 48.dp
    val buttonHeight = 52.dp
    val buttonHeightCompact = 44.dp
    val topAppBar = 56.dp
    val bottomNav = 80.dp
    val chipHeight = 32.dp
    val verdictChipHeight = 28.dp
    val countBubble = 28.dp
    val categoryTile = 48.dp
    val ribbonMinHeight = 36.dp
    val factChipMinHeight = 36.dp
    val navPillWidth = 64.dp
    val navPillHeight = 32.dp
    val iconSm = 18.dp
    val iconMd = 20.dp
    val icon = 24.dp
}
