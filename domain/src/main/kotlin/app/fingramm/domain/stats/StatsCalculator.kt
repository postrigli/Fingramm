package app.fingramm.domain.stats

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

    /**
     * Средний результат месяца (доходы − расходы) по последним полным месяцам с данными, не больше [maxMonths]:
     * по нему прогнозируется срок достижения целей. Null, если полных месяцев с данными ещё нет.
     */
    public fun averageMonthlyFlow(
        totals: Map<YearMonth, MonthTotals>,
        current: YearMonth,
        maxMonths: Int = DEFAULT_FLOW_MONTHS,
    ): Long? {
        val eligible = totals.values
            .filter { it.month < current && it.hasData }
            .sortedByDescending { it.month }
            .take(maxMonths)
        if (eligible.isEmpty()) return null
        return eligible.sumOf { it.balanceMinor } / eligible.size
    }

    /**
     * Сколько денег сейчас: остаток на начало учёта плюс все доходы минус все расходы за всё время.
     * Месяцы суммируются целиком, поэтому результат не зависит от дня начала месяца.
     */
    public fun currentBalance(openingMinor: Long, totals: Map<YearMonth, MonthTotals>): Long =
        openingMinor + totals.values.sumOf { it.balanceMinor }

    /**
     * Начальный остаток, при котором приложение покажет [actualMinor]: сверка с банком. Остаток на сегодня равен
     * начальному плюс результат всех операций, поэтому начальный = фактический − результат операций.
     */
    public fun openingFor(actualMinor: Long, totals: Map<YearMonth, MonthTotals>): Long =
        actualMinor - currentBalance(0, totals)

    public fun limitUsage(limit: CategoryLimit, spentMinor: Long): LimitUsage {
        val ratio = Money.ratio(spentMinor, limit.limitMinor) ?: if (spentMinor > 0) Double.POSITIVE_INFINITY else 0.0
        val status = when {
            limit.limitMinor <= 0 -> if (spentMinor > 0) LimitStatus.EXCEEDED else LimitStatus.OK
            spentMinor > limit.limitMinor -> LimitStatus.EXCEEDED
            Money.compareToPercent(spentMinor, limit.limitMinor, LIMIT_ATTENTION_PERCENT) >= 0 ->
                LimitStatus.ATTENTION
            else -> LimitStatus.OK
        }
        return LimitUsage(limit, spentMinor, ratio, status)
    }

    /** С какой доли лимита категория помечается «внимание». */
    public const val LIMIT_ATTENTION_PERCENT: Int = 80

    private const val DEFAULT_FLOW_MONTHS = 3
}
