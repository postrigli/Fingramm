package app.fingramm.domain.survey

import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.TimeCommitment

/** Режим напоминаний по умолчанию в зависимости от того, сколько времени готов уделять пользователь. */
public object ReminderDefaults {
    public fun suggested(commitment: TimeCommitment?): ReminderMode = when (commitment) {
        TimeCommitment.DAILY, TimeCommitment.FEW_MINUTES_DAILY -> ReminderMode.DAILY_EVENING
        // Раз в месяц — слишком редко для напоминаний; мягкий недельный режим достаточно ненавязчив.
        TimeCommitment.WEEKLY, TimeCommitment.MONTHLY -> ReminderMode.WEEKLY
        null -> ReminderMode.OFF
    }
}
