package app.fingramm.domain.report

import app.fingramm.domain.stats.MonthTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class MonthReportTest {
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)

    private fun month(
        month: YearMonth,
        income: Long,
        expenses: Map<Long, Long>,
    ) = MonthTotals(
        month = month,
        incomeMinor = income,
        variableMinor = expenses.values.sum(),
        expenseByCategory = expenses,
    )

    @Test
    fun `compares totals with the previous month`() {
        val report = MonthReports.build(
            month(october, 120_000, mapOf(1L to 50_000, 2L to 30_000)),
            month(september, 100_000, mapOf(1L to 40_000, 2L to 40_000)),
        )
        assertEquals(Change(20_000, 20), report.incomeChange)
        assertEquals(Change(0, 0), report.expenseChange)
    }

    @Test
    fun `no comparison without a previous month with data`() {
        val empty = MonthReports.build(month(october, 100, mapOf(1L to 50)), MonthTotals(september))
        assertNull(empty.incomeChange)
        assertNull(empty.expenseChange)
        assertNull(empty.biggestGrowth)
        assertNull(MonthReports.build(month(october, 100, mapOf(1L to 50)), null).incomeChange)
    }

    @Test
    fun `top categories are the biggest five with shares`() {
        val spends = (1L..7L).associateWith { it * 1_000 }
        val report = MonthReports.build(month(october, 0, spends), null)
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), report.topCategories.map { it.categoryId })
        assertEquals(25, report.topCategories.first().sharePercent) // 7000 из 28000
    }

    @Test
    fun `biggest growth is the category that grew the most in money`() {
        val report = MonthReports.build(
            month(october, 0, mapOf(1L to 50_000, 2L to 20_000, 3L to 9_000)),
            month(september, 0, mapOf(1L to 45_000, 2L to 5_000, 3L to 9_000)),
        )
        val growth = report.biggestGrowth
        assertEquals(2L, growth?.categoryId)
        assertEquals(15_000L, growth?.deltaMinor)
    }

    @Test
    fun `nothing grew means no growth highlight`() {
        val report = MonthReports.build(
            month(october, 0, mapOf(1L to 10_000)),
            month(september, 0, mapOf(1L to 12_000)),
        )
        assertNull(report.biggestGrowth)
    }

    @Test
    fun `a new category counts as growth from zero`() {
        val report = MonthReports.build(
            month(october, 0, mapOf(1L to 10_000, 9L to 4_000)),
            month(september, 0, mapOf(1L to 10_000)),
        )
        assertEquals(9L, report.biggestGrowth?.categoryId)
    }
}
