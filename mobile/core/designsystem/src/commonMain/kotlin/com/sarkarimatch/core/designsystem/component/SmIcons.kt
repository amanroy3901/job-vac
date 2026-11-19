package com.sarkarimatch.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.sarkarimatch.core.designsystem.resources.Res
import com.sarkarimatch.core.designsystem.resources.ic_account_balance
import com.sarkarimatch.core.designsystem.resources.ic_account_circle
import com.sarkarimatch.core.designsystem.resources.ic_account_circle_outline
import com.sarkarimatch.core.designsystem.resources.ic_alarm
import com.sarkarimatch.core.designsystem.resources.ic_arrow_back
import com.sarkarimatch.core.designsystem.resources.ic_arrow_forward
import com.sarkarimatch.core.designsystem.resources.ic_assignment_turned_in
import com.sarkarimatch.core.designsystem.resources.ic_assignment_turned_in_outline
import com.sarkarimatch.core.designsystem.resources.ic_cancel
import com.sarkarimatch.core.designsystem.resources.ic_check
import com.sarkarimatch.core.designsystem.resources.ic_check_circle
import com.sarkarimatch.core.designsystem.resources.ic_currency_rupee
import com.sarkarimatch.core.designsystem.resources.ic_event
import com.sarkarimatch.core.designsystem.resources.ic_help
import com.sarkarimatch.core.designsystem.resources.ic_info
import com.sarkarimatch.core.designsystem.resources.ic_local_police
import com.sarkarimatch.core.designsystem.resources.ic_location_city
import com.sarkarimatch.core.designsystem.resources.ic_lock
import com.sarkarimatch.core.designsystem.resources.ic_notifications_active
import com.sarkarimatch.core.designsystem.resources.ic_notifications_outline
import com.sarkarimatch.core.designsystem.resources.ic_savings
import com.sarkarimatch.core.designsystem.resources.ic_school
import com.sarkarimatch.core.designsystem.resources.ic_snooze
import com.sarkarimatch.core.designsystem.resources.ic_task_alt
import com.sarkarimatch.core.designsystem.resources.ic_timer
import com.sarkarimatch.core.designsystem.resources.ic_train
import com.sarkarimatch.core.designsystem.resources.ic_translate
import com.sarkarimatch.core.designsystem.resources.ic_work
import com.sarkarimatch.core.designsystem.resources.ic_work_outline
import com.sarkarimatch.core.designsystem.resources.ic_workspace_premium
import com.sarkarimatch.core.designsystem.theme.SmSize
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Material Symbols Rounded (filled, as Direction C specifies) imported as Compose Multiplatform vector drawables
 * (composeResources/drawable, Android vector XML) — no
 * material-icons-extended. Directional icons are autoMirrored for RTL.
 */
object SmIcons {
    val Alarm: DrawableResource = Res.drawable.ic_alarm
    val Timer: DrawableResource = Res.drawable.ic_timer
    val Event: DrawableResource = Res.drawable.ic_event
    val TaskAlt: DrawableResource = Res.drawable.ic_task_alt
    val Snooze: DrawableResource = Res.drawable.ic_snooze
    val CheckCircle: DrawableResource = Res.drawable.ic_check_circle
    val Check: DrawableResource = Res.drawable.ic_check
    val Help: DrawableResource = Res.drawable.ic_help
    val Cancel: DrawableResource = Res.drawable.ic_cancel
    val Info: DrawableResource = Res.drawable.ic_info
    val Lock: DrawableResource = Res.drawable.ic_lock
    val Rupee: DrawableResource = Res.drawable.ic_currency_rupee
    val School: DrawableResource = Res.drawable.ic_school
    val Police: DrawableResource = Res.drawable.ic_local_police
    val Ssc: DrawableResource = Res.drawable.ic_account_balance
    val Railway: DrawableResource = Res.drawable.ic_train
    val Bank: DrawableResource = Res.drawable.ic_savings
    val State: DrawableResource = Res.drawable.ic_location_city
    val Work: DrawableResource = Res.drawable.ic_work
    val WorkOutline: DrawableResource = Res.drawable.ic_work_outline
    val Forms: DrawableResource = Res.drawable.ic_assignment_turned_in
    val FormsOutline: DrawableResource = Res.drawable.ic_assignment_turned_in_outline
    val Profile: DrawableResource = Res.drawable.ic_account_circle
    val ProfileOutline: DrawableResource = Res.drawable.ic_account_circle_outline
    val Bell: DrawableResource = Res.drawable.ic_notifications_outline
    val BellActive: DrawableResource = Res.drawable.ic_notifications_active
    val Translate: DrawableResource = Res.drawable.ic_translate
    val ArrowForward: DrawableResource = Res.drawable.ic_arrow_forward
    val ArrowBack: DrawableResource = Res.drawable.ic_arrow_back
    val Pro: DrawableResource = Res.drawable.ic_workspace_premium
}

/** Decorative by default: every icon sits next to a word (Direction C), so TalkBack reads the word. */
@Composable
fun SmIcon(
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
    size: Dp = SmSize.icon,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}
