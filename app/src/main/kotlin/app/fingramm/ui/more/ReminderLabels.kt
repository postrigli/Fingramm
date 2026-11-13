package app.fingramm.ui.more

import androidx.annotation.StringRes
import app.fingramm.R
import app.fingramm.domain.model.ReminderMode

@StringRes
fun reminderModeLabel(mode: ReminderMode): Int =
    when (mode) {
        ReminderMode.DAILY_EVENING -> R.string.reminder_daily
        ReminderMode.WEEKLY -> R.string.reminder_weekly
        ReminderMode.OFF -> R.string.reminder_off
    }
