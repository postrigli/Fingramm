package app.fingramm.widget

import app.fingramm.domain.model.CurrencyCode
import java.time.YearMonth

/**
 * Что показывает виджет месяца. Месяц календарный (с 1 числа), как и во всём приложении.
 *
 * @property configured false, пока не пройден опрос: показатели считать не от чего.
 * @property amountsHidden суммы скрыты, потому что в приложении включена блокировка: виджет виден без неё.
 */
data class MonthWidgetState(
    val month: YearMonth,
    val incomeMinor: Long,
    val expensesMinor: Long,
    val currency: CurrencyCode,
    val configured: Boolean,
    val amountsHidden: Boolean,
) {
    val balanceMinor: Long get() = incomeMinor - expensesMinor
}
