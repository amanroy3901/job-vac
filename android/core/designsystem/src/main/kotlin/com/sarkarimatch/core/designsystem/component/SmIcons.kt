package com.sarkarimatch.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.sarkarimatch.core.designsystem.R
import com.sarkarimatch.core.designsystem.theme.SmSize

/**
 * Material Symbols Rounded (filled, as Direction C specifies) imported as vector drawables — no
 * material-icons-extended. Directional icons are autoMirrored for RTL.
 */
object SmIcons {
    @DrawableRes val Alarm = R.drawable.ic_alarm
    @DrawableRes val Timer = R.drawable.ic_timer
    @DrawableRes val Event = R.drawable.ic_event
    @DrawableRes val TaskAlt = R.drawable.ic_task_alt
    @DrawableRes val Snooze = R.drawable.ic_snooze
    @DrawableRes val CheckCircle = R.drawable.ic_check_circle
    @DrawableRes val Check = R.drawable.ic_check
    @DrawableRes val Help = R.drawable.ic_help
    @DrawableRes val Cancel = R.drawable.ic_cancel
    @DrawableRes val Info = R.drawable.ic_info
    @DrawableRes val Lock = R.drawable.ic_lock
    @DrawableRes val Rupee = R.drawable.ic_currency_rupee
    @DrawableRes val School = R.drawable.ic_school
    @DrawableRes val Police = R.drawable.ic_local_police
    @DrawableRes val Ssc = R.drawable.ic_account_balance
    @DrawableRes val Railway = R.drawable.ic_train
    @DrawableRes val Bank = R.drawable.ic_savings
    @DrawableRes val State = R.drawable.ic_location_city
    @DrawableRes val Work = R.drawable.ic_work
    @DrawableRes val WorkOutline = R.drawable.ic_work_outline
    @DrawableRes val Forms = R.drawable.ic_assignment_turned_in
    @DrawableRes val FormsOutline = R.drawable.ic_assignment_turned_in_outline
    @DrawableRes val Profile = R.drawable.ic_account_circle
    @DrawableRes val ProfileOutline = R.drawable.ic_account_circle_outline
    @DrawableRes val Bell = R.drawable.ic_notifications_outline
    @DrawableRes val BellActive = R.drawable.ic_notifications_active
    @DrawableRes val Translate = R.drawable.ic_translate
    @DrawableRes val ArrowForward = R.drawable.ic_arrow_forward
    @DrawableRes val ArrowBack = R.drawable.ic_arrow_back
    @DrawableRes val Pro = R.drawable.ic_workspace_premium
}

/** Decorative by default: every icon sits next to a word (Direction C), so TalkBack reads the word. */
@Composable
fun SmIcon(
    @DrawableRes icon: Int,
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
