package com.sarkarimatch.core.designsystem.component

import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarkarimatch.core.designsystem.theme.SmTheme
import com.sarkarimatch.core.designsystem.theme.tabular

/** Bottom navigation with the marigold nav pill (Direction C). Three tabs: Jobs / My forms / Profile. */
@Composable
fun SmNavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    NavigationBar(
        modifier = modifier,
        containerColor = SmTheme.colors.glass,
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
fun RowScope.SmNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: DrawableResource,
    selectedIcon: DrawableResource,
    badge: Int? = null,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            BadgedBox(badge = {
                if (badge != null && badge > 0) {
                    Badge(containerColor = SmTheme.colors.today, contentColor = SmTheme.colors.onToday) {
                        Text(if (badge > 9) "9+" else "$badge", style = MaterialTheme.typography.labelSmall.tabular())
                    }
                }
            }) {
                SmIcon(if (selected) selectedIcon else icon)
            }
        },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = SmTheme.colors.onMarigold,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = SmTheme.colors.marigold,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
