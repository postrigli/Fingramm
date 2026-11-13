package app.fingramm.domain.report

import app.fingramm.domain.money.Money
import app.fingramm.domain.stats.MonthTotals

/** Категория в отчёте: сколько потрачено, доля в расходах месяца и сколько было в прошлом месяце. */
public data class CategorySpend(
    val categoryId: Long,
    val amountMinor: Long,
    /** Доля в расходах месяца в процентах (0–100); null, если расходов не было. */
    val sharePercent: Int?,
    val previousMinor: Long,
) {
    public val deltaMinor: Long get() = amountMinor - previousMinor
}

/** Сравнение с прошлым месяцем: разность в минимальных единицах и в процентах (null — сравнивать не с чем). */
public data class Change(val deltaMinor: Long, val percent: Int?)

/** Отчёт месяца только по фактическим операциям. */
public data class MonthReport(
    val totals: MonthTotals,
    val previous: MonthTotals?,
    val incomeChange: Change?,
    val expenseChange: Change?,
    /** До [MonthReports.TOP_COUNT] самых затратных категорий. */
    val topCategories: List<CategorySpend>,
    /** Категория с самым большим ростом расходов к прошлому месяцу; null, если ничего не выросло. */
    val biggestGrowth: CategorySpend?,
) {
    val hasData: Boolean get() = totals.hasData
}

public object MonthReports {
    public const val TOP_COUNT: Int = 5

    public fun build(totals: MonthTotals, previous: MonthTotals?): MonthReport {
        val previousExpenses = previous?.expenseByCategory.orEmpty()
        val spends = totals.expenseByCategory.entries
            .filter { it.value > 0 }
            .map { (id, amount) ->
                CategorySpend(
                    categoryId = id,
                    amountMinor = amount,
                    sharePercent = percentOf(amount, totals.expensesMinor),
                    previousMinor = previousExpenses[id] ?: 0L,
                )
            }
        val growth = if (previous?.hasData == true) {
            spends.filter { it.deltaMinor > 0 }.maxWithOrNull(
                compareBy<CategorySpend> { it.deltaMinor }.thenByDescending { it.categoryId },
            )
        } else {
            null
        }
        return MonthReport(
            totals = totals,
            previous = previous,
            incomeChange = previous?.takeIf { it.hasData }?.let { change(totals.incomeMinor, it.incomeMinor) },
            expenseChange = previous?.takeIf { it.hasData }?.let { change(totals.expensesMinor, it.expensesMinor) },
            topCategories = spends.sortedWith(biggestFirst).take(TOP_COUNT),
            biggestGrowth = growth,
        )
    }

    private val biggestFirst = compareByDescending<CategorySpend> { it.amountMinor }.thenBy { it.categoryId }

    private fun change(current: Long, previous: Long): Change =
        Change(current - previous, percentOf(current - previous, previous))

    private fun percentOf(numerator: Long, denominator: Long): Int? =
        Money.ratio(numerator, denominator)?.let { Math.round(it * PERCENT).toInt() }

    private const val PERCENT = 100
}
