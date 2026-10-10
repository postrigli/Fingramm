package app.fingramm.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class FormatPeriodTest {
    @Test
    fun `calendar month is shown by name`() {
        assertEquals(formatMonth(YearMonth.of(2026, 10)), formatPeriod(YearMonth.of(2026, 10), 1))
    }

    @Test
    fun `a shifted period is shown by its dates with the year at the end`() {
        val label = formatPeriod(YearMonth.of(2026, 10), 25)
        assertTrue(label, label.startsWith("25 "))
        assertTrue(label, label.contains(" – 24 "))
        assertTrue(label, label.endsWith(" 2026"))
        // Годы одинаковы, поэтому у начала года нет.
        assertTrue(label, !label.substringBefore(" – ").contains("2026"))
    }

    @Test
    fun `a period across the new year shows both years`() {
        val label = formatPeriod(YearMonth.of(2026, 12), 10)
        assertTrue(label, label.startsWith("10 ") && label.contains("2026 – 9 ") && label.endsWith(" 2027"))
    }
}
