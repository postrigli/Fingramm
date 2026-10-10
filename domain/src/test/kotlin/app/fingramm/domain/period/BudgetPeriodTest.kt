package app.fingramm.domain.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetPeriodTest {
    private val october = YearMonth.of(2026, 10)

    @Test
    fun `day 1 is the calendar month`() {
        assertEquals(october, BudgetPeriod.containing(LocalDate.of(2026, 10, 1), 1))
        assertEquals(october, BudgetPeriod.containing(LocalDate.of(2026, 10, 31), 1))
        assertEquals(LocalDate.of(2026, 10, 1), BudgetPeriod.start(october, 1))
        assertEquals(LocalDate.of(2026, 10, 31), BudgetPeriod.end(october, 1))
        assertEquals(0, BudgetPeriod.shiftDays(1))
    }

    @Test
    fun `a period is named after the month in which it starts`() {
        // Начало с 25 числа: «октябрь» = 25 октября – 24 ноября.
        assertEquals(LocalDate.of(2026, 10, 25), BudgetPeriod.start(october, 25))
        assertEquals(LocalDate.of(2026, 11, 24), BudgetPeriod.end(october, 25))
        assertEquals(24, BudgetPeriod.shiftDays(25))
    }

    @Test
    fun `dates around the boundary fall into the right period`() {
        assertEquals(YearMonth.of(2026, 9), BudgetPeriod.containing(LocalDate.of(2026, 10, 24), 25))
        assertEquals(october, BudgetPeriod.containing(LocalDate.of(2026, 10, 25), 25))
        assertEquals(october, BudgetPeriod.containing(LocalDate.of(2026, 11, 24), 25))
        assertEquals(YearMonth.of(2026, 11), BudgetPeriod.containing(LocalDate.of(2026, 11, 25), 25))
    }

    @Test
    fun `periods cross the year boundary`() {
        val december = YearMonth.of(2026, 12)
        assertEquals(LocalDate.of(2026, 12, 10), BudgetPeriod.start(december, 10))
        assertEquals(LocalDate.of(2027, 1, 9), BudgetPeriod.end(december, 10))
        assertEquals(december, BudgetPeriod.containing(LocalDate.of(2027, 1, 9), 10))
        assertEquals(YearMonth.of(2027, 1), BudgetPeriod.containing(LocalDate.of(2027, 1, 10), 10))
    }

    @Test
    fun `day 28 works in every month including February`() {
        val january = YearMonth.of(2027, 1)
        assertEquals(LocalDate.of(2027, 1, 28), BudgetPeriod.start(january, 28))
        // Февраль 2027 не високосный: период января заканчивается 27 февраля.
        assertEquals(LocalDate.of(2027, 2, 27), BudgetPeriod.end(january, 28))
        assertEquals(january, BudgetPeriod.containing(LocalDate.of(2027, 2, 27), 28))
        assertEquals(YearMonth.of(2027, 2), BudgetPeriod.containing(LocalDate.of(2027, 2, 28), 28))
        // Високосный 2028: после 28 февраля идёт 29-е, оно ещё в февральском периоде.
        assertEquals(YearMonth.of(2028, 2), BudgetPeriod.containing(LocalDate.of(2028, 3, 27), 28))
        assertEquals(YearMonth.of(2028, 3), BudgetPeriod.containing(LocalDate.of(2028, 3, 28), 28))
    }

    @Test
    fun `every date belongs to exactly the period whose range contains it`() {
        for (startDay in 1..BudgetPeriod.MAX_START_DAY) {
            var date = LocalDate.of(2027, 12, 1)
            while (date.isBefore(LocalDate.of(2029, 3, 1))) {
                val period = BudgetPeriod.containing(date, startDay)
                val start = BudgetPeriod.start(period, startDay)
                val end = BudgetPeriod.end(period, startDay)
                assertTrue("$date, day $startDay, $period", !date.isBefore(start) && !date.isAfter(end))
                date = date.plusDays(1)
            }
        }
    }

    @Test
    fun `consecutive periods tile the calendar without gaps or overlaps`() {
        for (startDay in 1..BudgetPeriod.MAX_START_DAY) {
            var period = YearMonth.of(2027, 1)
            repeat(30) {
                val next = period.plusMonths(1)
                assertEquals(
                    "day $startDay, $period",
                    BudgetPeriod.start(next, startDay),
                    BudgetPeriod.end(period, startDay).plusDays(1),
                )
                period = next
            }
        }
    }

    @Test
    fun `out of range days are clamped`() {
        assertEquals(1, BudgetPeriod.normalize(0))
        assertEquals(1, BudgetPeriod.normalize(-5))
        assertEquals(28, BudgetPeriod.normalize(31))
        assertEquals(LocalDate.of(2026, 10, 28), BudgetPeriod.start(october, 99))
        assertEquals(27, BudgetPeriod.shiftDays(99))
    }
}
