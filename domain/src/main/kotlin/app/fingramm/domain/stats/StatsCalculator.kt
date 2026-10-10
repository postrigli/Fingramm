package app.fingramm.domain.stats

import app.fingramm.domain.metrics.Benchmarks
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryKind
import app.fingramm.domain.model.CategoryLimit
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.money.Money
import java.time.YearMonth

/** Сумма операций одного типа в одной категории за месяц — строка агрегата из БД. */
public data class MonthCategoryTotal(
    val month: YearMonth,
    val categoryId: Long,
    val type: TransactionType,
    val totalMinor: Long,
)

/** Итоги месяца, разложенные по влиянию на показатели. */
public data class MonthTotals(
    val month: YearMonth,
    val incomeMinor: Long = 0,
    val mandatoryMinor: Long = 0,
    val variableMinor: Long = 0,
    val debtPaymentMinor: Long = 0,
    /** Расходы по категориям (id → сумма). */
    val expenseByCategory: Map<Long, Long> = emptyMap(),
    val incomeByCategory: Map<Long, Long> = emptyMap(),
) {
    val expensesMinor: Long get() = mandatoryMinor + variableMinor + debtPaymentMinor
    val balanceMinor: Long get() = incomeMinor - expensesMinor
    val hasData: Boolean get() = incomeMinor != 0L || expensesMinor != 0L
}

public enum class LimitStatus { OK, ATTENTION, EXCEEDED }

public data class LimitUsage(
    val limit: CategoryLimit,
    val spentMinor: Long,
    /** Доля использования; может быть больше 1. */
    val ratio: Double,
    val status: LimitStatus,
)

public object StatsCalculator {
    /** Сворачивает агрегаты из БД в итоги по месяцам. Неизвестные категории считаются переменными. */
    public fun monthTotals(
        rows: List<MonthCategoryTotal>,
        categories: Map<Long, Category>,
    ): Map<YearMonth, MonthTotals> = rows.groupBy { it.month }.mapValues { (month, monthRows) ->
        var totals = MonthTotals(month)
        val expenses = mutableMapOf<Long, Long>()
        val incomes = mutableMapOf<Long, Long>()
        for (row in monthRows) {
            when (row.type) {
                TransactionType.INCOME -> {
                    totals = totals.copy(incomeMinor = totals.incomeMinor + row.totalMinor)
                    incomes.merge(row.categoryId, row.totalMinor, Long::plus)
                }
                TransactionType.EXPENSE -> {
                    val category = categories[row.categoryId]
                    totals = when {
                        category?.isDebtPayment == true ->
                            totals.copy(debtPaymentMinor = totals.debtPaymentMinor + row.totalMinor)
                        category?.kind == CategoryKind.MANDATORY ->
                            totals.copy(mandatoryMinor = totals.mandatoryMinor + row.totalMinor)
                        else -> totals.copy(variableMinor = totals.variableMinor + row.totalMinor)
                    }
                    expenses.merge(row.categoryId, row.totalMinor, Long::plus)
                }
            }
        }
        totals.copy(expenseByCategory = expenses, incomeByCategory = incomes)
    }

    /** Накопленный результат (доходы − расходы) по всем месяцам до указанного включительно. */
    public fun cumulativeBalanceUpTo(totals: Map<YearMonth, MonthTotals>, month: YearMonth): Long =
        totals.filterKeys { it <= month }.values.sumOf { it.balanceMinor }

    /**
     * Оценка переменных расходов из фактических операций: среднее по последним полным месяцам
     * (не более [maxMonths]), следующим за месяцем создания профиля и предшествующим текущему.
     * Null, если подходящих месяцев с данными ещё нет.
     */
    public fun estimateVariableFromFacts(
        totals: Map<YearMonth, MonthTotals>,
        profileCreated: YearMonth,
        current: YearMonth,
        maxMonths: Int = DEFAULT_ESTIMATE_MONTHS,
    ): Long? {
        val eligible = totals.values
            .filter { it.month > profileCreated && it.month < current && it.hasData }
            .sortedByDescending { it.month }
            .take(maxMonths)
        if (eligible.isEmpty()) return null
        return eligible.sumOf { it.variableMinor } / eligible.size
    }

    public fun limitUsage(limit: CategoryLimit, spentMinor: Long): LimitUsage {
        val ratio = Money.ratio(spentMinor, limit.limitMinor) ?: if (spentMinor > 0) Double.POSITIVE_INFINITY else 0.0
        val status = when {
            limit.limitMinor <= 0 -> if (spentMinor > 0) LimitStatus.EXCEEDED else LimitStatus.OK
            spentMinor > limit.limitMinor -> LimitStatus.EXCEEDED
            Money.compareToPercent(spentMinor, limit.limitMinor, Benchmarks.LIMIT_ATTENTION_PERCENT) >= 0 ->
                LimitStatus.ATTENTION
            else -> LimitStatus.OK
        }
        return LimitUsage(limit, spentMinor, ratio, status)
    }

    private const val DEFAULT_ESTIMATE_MONTHS = 3
}
