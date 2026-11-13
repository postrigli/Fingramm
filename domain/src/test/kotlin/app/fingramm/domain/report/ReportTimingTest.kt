package app.fingramm.domain.report

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class ReportTimingTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    @Test
    fun `calendar month report comes on the first at ten`() {
        assertEquals(at(2026, 11, 1, 10), ReportTiming.nextTrigger(at(2026, 10, 15, 12), 1))
        assertEquals(at(2026, 11, 1, 10), ReportTiming.nextTrigger(at(2026, 11, 1, 9, 59), 1))
        assertEquals(at(2026, 12, 1, 10), ReportTiming.nextTrigger(at(2026, 11, 1, 10), 1))
    }

    @Test
    fun `shifted month report comes on the start day`() {
        assertEquals(at(2026, 10, 25, 10), ReportTiming.nextTrigger(at(2026, 10, 3, 8), 25))
        assertEquals(at(2026, 11, 25, 10), ReportTiming.nextTrigger(at(2026, 10, 25, 11), 25))
        assertEquals(at(2027, 1, 25, 10), ReportTiming.nextTrigger(at(2026, 12, 30, 8), 25))
    }

    @Test
    fun `report is made for the last closed period`() {
        assertEquals(YearMonth.of(2026, 10), ReportTiming.closedPeriod(LocalDate.of(2026, 11, 1), 1))
        assertEquals(YearMonth.of(2026, 9), ReportTiming.closedPeriod(LocalDate.of(2026, 10, 31), 1))
        assertEquals(YearMonth.of(2026, 9), ReportTiming.closedPeriod(LocalDate.of(2026, 10, 25), 25))
        assertEquals(YearMonth.of(2026, 8), ReportTiming.closedPeriod(LocalDate.of(2026, 10, 24), 25))
    }
}
