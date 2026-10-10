package app.fingramm.domain.stats

import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryKind
import app.fingramm.domain.model.CategoryLimit
import app.fingramm.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class StatsCalculatorTest {
    private val categories = listOf(
        Category(1, "Продукты", TransactionType.EXPENSE, CategoryKind.MANDATORY, "🛒"),
        Category(2, "Кафе", TransactionType.EXPENSE, CategoryKind.VARIABLE, "☕"),
        Category(3, "Кредит", TransactionType.EXPENSE, CategoryKind.MANDATORY, "💳", isDebtPayment = true),
        Category(4, "Зарплата", TransactionType.INCOME, CategoryKind.VARIABLE, "💼"),
    ).associateBy { it.id }

    private val oct = YearMonth.of(2026, 10)
    private val sep = YearMonth.of(2026, 9)
    private val aug = YearMonth.of(2026, 8)
    private val jul = YearMonth.of(2026, 7)

    private fun row(month: YearMonth, category: Long, type: TransactionType, total: Long) =
        MonthCategoryTotal(month, category, type, total)

    @Test
    fun `month totals split expenses by category role`() {
        val totals = StatsCalculator.monthTotals(
            listOf(
                row(oct, 4, TransactionType.INCOME, 100_000),
                row(oct, 1, TransactionType.EXPENSE, 30_000),
                row(oct, 2, TransactionType.EXPENSE, 12_000),
                row(oct, 3, TransactionType.EXPENSE, 15_000),
                row(sep, 1, TransactionType.EXPENSE, 1_000),
            ),
            categories,
        )
        val october = totals.getValue(oct)
        assertEquals(100_000, october.incomeMinor)
        assertEquals(30_000, october.mandatoryMinor)
        assertEquals(12_000, october.variableMinor)
        assertEquals(15_000, october.debtPaymentMinor)
        assertEquals(57_000, october.expensesMinor)
        assertEquals(43_000, october.balanceMinor)
        assertEquals(mapOf(1L to 30_000L, 2L to 12_000L, 3L to 15_000L), october.expenseByCategory)
        assertEquals(1_000, totals.getValue(sep).mandatoryMinor)
    }

    @Test
    fun `unknown category counts as variable`() {
        val totals = StatsCalculator.monthTotals(listOf(row(oct, 99, TransactionType.EXPENSE, 500)), categories)
        assertEquals(500, totals.getValue(oct).variableMinor)
    }

    @Test
    fun `cumulative balance includes only months up to the selected one`() {
        val totals = StatsCalculator.monthTotals(
            listOf(
                row(aug, 4, TransactionType.INCOME, 100),
                row(sep, 1, TransactionType.EXPENSE, 30),
                row(oct, 4, TransactionType.INCOME, 1_000),
            ),
            categories,
        )
        assertEquals(70, StatsCalculator.cumulativeBalanceUpTo(totals, sep))
        assertEquals(1_070, StatsCalculator.cumulativeBalanceUpTo(totals, oct))
        assertEquals(0, StatsCalculator.cumulativeBalanceUpTo(totals, YearMonth.of(2026, 1)))
    }

    @Test
    fun `variable estimate averages full months after profile creation`() {
        val totals = StatsCalculator.monthTotals(
            listOf(
                // Месяц создания профиля — неполный, не учитывается.
                row(jul, 2, TransactionType.EXPENSE, 99_999),
                row(aug, 2, TransactionType.EXPENSE, 10_000),
                row(sep, 2, TransactionType.EXPENSE, 20_000),
                // Текущий месяц ещё не завершён.
                row(oct, 2, TransactionType.EXPENSE, 77_777),
            ),
            categories,
        )
        assertEquals(15_000L, StatsCalculator.estimateVariableFromFacts(totals, jul, oct))
    }

    @Test
    fun `variable estimate is null until a full month has passed`() {
        val totals = StatsCalculator.monthTotals(listOf(row(oct, 2, TransactionType.EXPENSE, 5_000)), categories)
        assertNull(StatsCalculator.estimateVariableFromFacts(totals, oct, oct))
        assertNull(StatsCalculator.estimateVariableFromFacts(emptyMap(), jul, oct))
    }

    @Test
    fun `variable estimate uses at most the last three months`() {
        val totals = StatsCalculator.monthTotals(
            listOf(
                row(YearMonth.of(2026, 1), 2, TransactionType.EXPENSE, 1_000_000),
                row(YearMonth.of(2026, 6), 2, TransactionType.EXPENSE, 30),
                row(jul, 2, TransactionType.EXPENSE, 60),
                row(aug, 2, TransactionType.EXPENSE, 90),
            ),
            categories,
        )
        assertEquals(60L, StatsCalculator.estimateVariableFromFacts(totals, YearMonth.of(2025, 12), sep))
    }

    @Test
    fun `limit usage statuses`() {
        val limit = CategoryLimit(2, 10_000)
        assertEquals(LimitStatus.OK, StatsCalculator.limitUsage(limit, 7_999).status)
        assertEquals(LimitStatus.ATTENTION, StatsCalculator.limitUsage(limit, 8_000).status)
        assertEquals(LimitStatus.ATTENTION, StatsCalculator.limitUsage(limit, 10_000).status)
        assertEquals(LimitStatus.EXCEEDED, StatsCalculator.limitUsage(limit, 10_001).status)
        assertEquals(1.5, StatsCalculator.limitUsage(limit, 15_000).ratio, 1e-9)
    }
}
