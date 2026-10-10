package app.fingramm.notifications

import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.ReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ReminderTimingTest {
    // 10 октября 2026 — суббота.
    private val saturdayNoon = LocalDateTime.of(2026, 10, 10, 12, 0)

    private fun daily(hour: Int = 20, minute: Int = 30) =
        ReminderSettings(ReminderMode.DAILY_EVENING, hour, minute)

    private fun weekly(isoDay: Int, hour: Int = 19, minute: Int = 0) =
        ReminderSettings(ReminderMode.WEEKLY, hour, minute, isoDay)

    @Test
    fun `off schedules nothing`() {
        assertNull(ReminderTiming.nextTrigger(ReminderSettings(ReminderMode.OFF), saturdayNoon))
    }

    @Test
    fun `daily fires today when the time has not passed`() {
        assertEquals(LocalDateTime.of(2026, 10, 10, 20, 30), ReminderTiming.nextTrigger(daily(), saturdayNoon))
    }

    @Test
    fun `daily moves to tomorrow when the time has passed`() {
        val late = LocalDateTime.of(2026, 10, 10, 21, 0)
        assertEquals(LocalDateTime.of(2026, 10, 11, 20, 30), ReminderTiming.nextTrigger(daily(), late))
    }

    @Test
    fun `daily at exactly the trigger time moves to tomorrow`() {
        val exact = LocalDateTime.of(2026, 10, 10, 20, 30)
        assertEquals(LocalDateTime.of(2026, 10, 11, 20, 30), ReminderTiming.nextTrigger(daily(), exact))
    }

    @Test
    fun `daily rolls over month and year`() {
        val newYearsEve = LocalDateTime.of(2026, 12, 31, 23, 0)
        assertEquals(LocalDateTime.of(2027, 1, 1, 20, 30), ReminderTiming.nextTrigger(daily(), newYearsEve))
    }

    @Test
    fun `weekly picks the next matching weekday`() {
        val next = ReminderTiming.nextTrigger(weekly(DayOfWeek.MONDAY.value), saturdayNoon)
        assertEquals(LocalDateTime.of(2026, 10, 12, 19, 0), next)
    }

    @Test
    fun `weekly today before the time fires today after the time next week`() {
        val settings = weekly(DayOfWeek.SATURDAY.value, hour = 18)
        assertEquals(LocalDateTime.of(2026, 10, 10, 18, 0), ReminderTiming.nextTrigger(settings, saturdayNoon))
        val evening = LocalDateTime.of(2026, 10, 10, 19, 0)
        assertEquals(LocalDateTime.of(2026, 10, 17, 18, 0), ReminderTiming.nextTrigger(settings, evening))
    }
}
