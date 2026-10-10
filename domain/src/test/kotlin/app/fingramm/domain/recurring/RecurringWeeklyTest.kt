package app.fingramm.domain.recurring

import app.fingramm.domain.model.RecurrenceFrequency
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class RecurringWeeklyTest {
    // 10 октября 2026 — суббота.
    private val saturday = LocalDate.of(2026, 10, 10)
    private val monWedFri = setOf(1, 3, 5)

    private fun weekly(
        days: Set<Int>,
        next: LocalDate = saturday,
        notifyAt: Int? = null,
        active: Boolean = true,
    ) = RecurringTemplate(
        id = 1,
        type = TransactionType.EXPENSE,
        amountMinor = 100,
        categoryId = 1,
        dayOfMonth = 1,
        nextDueDate = next,
        active = active,
        frequency = RecurrenceFrequency.WEEKLY,
        weekdays = days,
        notifyAtMinute = notifyAt,
    )

    private fun monthly(day: Int, notifyAt: Int?) = RecurringTemplate(
        id = 2,
        type = TransactionType.EXPENSE,
        amountMinor = 100,
        categoryId = 1,
        dayOfMonth = day,
        nextDueDate = saturday,
        notifyAtMinute = notifyAt,
    )

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    @Test
    fun `first due is the nearest selected weekday`() {
        val rule = Recurrence.weekly(monWedFri)
        assertEquals(LocalDate.of(2026, 10, 12), RecurringSchedule.firstDueOnOrAfter(saturday, rule)) // понедельник
        assertEquals(LocalDate.of(2026, 10, 12), RecurringSchedule.firstDueOnOrAfter(LocalDate.of(2026, 10, 12), rule))
    }

    @Test
    fun `next is the following selected weekday and wraps to next week`() {
        val rule = Recurrence.weekly(monWedFri)
        assertEquals(LocalDate.of(2026, 10, 14), RecurringSchedule.nextAfter(LocalDate.of(2026, 10, 12), rule))
        assertEquals(LocalDate.of(2026, 10, 16), RecurringSchedule.nextAfter(LocalDate.of(2026, 10, 14), rule))
        assertEquals(LocalDate.of(2026, 10, 19), RecurringSchedule.nextAfter(LocalDate.of(2026, 10, 16), rule))
    }

    @Test
    fun `a single weekday repeats every seven days`() {
        val rule = Recurrence.weekly(setOf(DayOfWeek.SUNDAY.value))
        val sunday = LocalDate.of(2026, 10, 11)
        assertEquals(sunday, RecurringSchedule.firstDueOnOrAfter(saturday, rule))
        assertEquals(LocalDate.of(2026, 10, 18), RecurringSchedule.nextAfter(sunday, rule))
    }

    @Test
    fun `an empty or invalid weekday set never fails`() {
        assertEquals(saturday, RecurringSchedule.firstDueOnOrAfter(saturday, Recurrence.weekly(emptySet())))
        assertEquals(LocalDate.of(2026, 10, 17), RecurringSchedule.nextAfter(saturday, Recurrence.weekly(emptySet())))
        assertEquals(saturday, RecurringSchedule.firstDueOnOrAfter(saturday, Recurrence.weekly(setOf(0, 9))))
    }

    @Test
    fun `pending lists every missed weekly occurrence in order`() {
        val template = weekly(monWedFri, next = LocalDate.of(2026, 10, 12))
        val dates = RecurringSchedule.pending(listOf(template), LocalDate.of(2026, 10, 17)).map { it.dueDate }
        assertEquals(listOf(12, 14, 16).map { LocalDate.of(2026, 10, it) }, dates)
    }

    @Test
    fun `the monthly overloads still work`() {
        assertEquals(LocalDate.of(2026, 10, 25), RecurringSchedule.firstDueOnOrAfter(saturday, 25))
        assertEquals(LocalDate.of(2026, 11, 25), RecurringSchedule.nextAfter(LocalDate.of(2026, 10, 25), 25))
    }

    // ---------- Время уведомления ----------

    @Test
    fun `monthly notification is later today, later this month or next month`() {
        val nineAm = 9 * 60
        assertEquals(at(15, 9), RecurringSchedule.nextNotification(monthly(15, nineAm), at(10, 12)))
        assertEquals(at(15, 9), RecurringSchedule.nextNotification(monthly(15, nineAm), at(15, 8, 59)))
        // В 9:00 ровно и позже — следующий месяц.
        val november = LocalDateTime.of(2026, 11, 15, 9, 0)
        assertEquals(november, RecurringSchedule.nextNotification(monthly(15, nineAm), at(15, 9)))
        assertEquals(november, RecurringSchedule.nextNotification(monthly(15, nineAm), at(15, 20)))
    }

    @Test
    fun `weekly notification picks the next selected weekday`() {
        val template = weekly(monWedFri, notifyAt = 7 * 60 + 30)
        assertEquals(at(12, 7, 30), RecurringSchedule.nextNotification(template, at(10, 12))) // суббота → понедельник
        assertEquals(at(14, 7, 30), RecurringSchedule.nextNotification(template, at(12, 7, 30))) // время уже наступило
        assertEquals(at(12, 7, 30), RecurringSchedule.nextNotification(template, at(12, 7, 29)))
    }

    @Test
    fun `no notification for paused templates or without a time`() {
        assertNull(RecurringSchedule.nextNotification(weekly(monWedFri, notifyAt = null), at(10, 12)))
        assertNull(RecurringSchedule.nextNotification(weekly(monWedFri, notifyAt = 540, active = false), at(10, 12)))
    }

    @Test
    fun `midnight and the last minute of the day are valid times`() {
        assertEquals(at(15, 0), RecurringSchedule.nextNotification(monthly(15, 0), at(14, 23)))
        assertEquals(at(15, 23, 59), RecurringSchedule.nextNotification(monthly(15, 23 * 60 + 59), at(15, 23, 58)))
    }
}
