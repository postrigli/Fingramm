package app.fingramm.domain.recurring

import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurringScheduleTest {
    private fun template(id: Long, day: Int, next: LocalDate, active: Boolean = true) = RecurringTemplate(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = 100,
        categoryId = 1,
        dayOfMonth = day,
        nextDueDate = next,
        active = active,
    )

    @Test
    fun `day 31 is clamped to month end`() {
        assertEquals(LocalDate.of(2026, 2, 28), RecurringSchedule.occurrence(YearMonth.of(2026, 2), 31))
        assertEquals(LocalDate.of(2028, 2, 29), RecurringSchedule.occurrence(YearMonth.of(2028, 2), 30))
        assertEquals(LocalDate.of(2026, 4, 30), RecurringSchedule.occurrence(YearMonth.of(2026, 4), 31))
    }

    @Test
    fun `next occurrence returns to the original day after a short month`() {
        val feb = LocalDate.of(2026, 2, 28)
        assertEquals(LocalDate.of(2026, 3, 31), RecurringSchedule.nextAfter(feb, 31))
        assertEquals(LocalDate.of(2027, 1, 15), RecurringSchedule.nextAfter(LocalDate.of(2026, 12, 15), 15))
    }

    @Test
    fun `first due date is today or later`() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals(today, RecurringSchedule.firstDueOnOrAfter(today, 10))
        assertEquals(LocalDate.of(2026, 10, 25), RecurringSchedule.firstDueOnOrAfter(today, 25))
        assertEquals(LocalDate.of(2026, 11, 5), RecurringSchedule.firstDueOnOrAfter(today, 5))
    }

    @Test
    fun `pending lists every missed occurrence in order`() {
        val today = LocalDate.of(2026, 10, 10)
        val pending = RecurringSchedule.pending(listOf(template(1, 5, LocalDate.of(2026, 8, 5))), today)
        assertEquals(
            listOf(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5)),
            pending.map { it.dueDate },
        )
    }

    @Test
    fun `future and inactive templates produce nothing`() {
        val today = LocalDate.of(2026, 10, 10)
        val templates = listOf(
            template(1, 20, LocalDate.of(2026, 10, 20)),
            template(2, 5, LocalDate.of(2026, 10, 5), active = false),
        )
        assertTrue(RecurringSchedule.pending(templates, today).isEmpty())
    }

    @Test
    fun `pending is capped per template and sorted across templates`() {
        val today = LocalDate.of(2026, 10, 10)
        val old = template(1, 1, LocalDate.of(2020, 1, 1))
        val recent = template(2, 2, LocalDate.of(2026, 10, 2))
        val result = RecurringSchedule.pending(listOf(old, recent), today)
        assertEquals(RecurringSchedule.MAX_PENDING_PER_TEMPLATE + 1, result.size)
        assertEquals(result.sortedBy { it.dueDate }.map { it.dueDate }, result.map { it.dueDate })
    }
}
