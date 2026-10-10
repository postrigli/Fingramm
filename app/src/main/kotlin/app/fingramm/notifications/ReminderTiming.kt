package app.fingramm.notifications

import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.ReminderSettings
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Расчёт ближайшего срабатывания напоминания. Чистая функция — проверяется unit-тестом. */
object ReminderTiming {
    /** Ближайший момент строго после [now] или null, если напоминания выключены. */
    fun nextTrigger(settings: ReminderSettings, now: LocalDateTime): LocalDateTime? {
        val atTime = now.toLocalDate().atTime(settings.hour, settings.minute)
        return when (settings.mode) {
            ReminderMode.OFF -> null
            ReminderMode.DAILY_EVENING -> if (atTime.isAfter(now)) atTime else atTime.plusDays(1)
            ReminderMode.WEEKLY -> {
                val target = DayOfWeek.of(settings.weekday)
                var candidate = atTime
                while (candidate.dayOfWeek != target || !candidate.isAfter(now)) {
                    candidate = candidate.plusDays(1)
                }
                candidate
            }
        }
    }
}
