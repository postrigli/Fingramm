package app.fingramm.widget

import app.fingramm.domain.model.CurrencyCode
import java.time.YearMonth

/**
 * Что показывает виджет месяца. Месяц расчётный, как и во всём приложении: календарный или начинающийся с
 * [startDay] (настройка «День начала месяца»).
 *
 * @property configured false, пока не пройден опрос: показатели считать не от чего.
 * @property amountsHidden суммы скрыты, потому что в приложении включена блокировка: виджет виден без неё.
 */
data class MonthWidgetState(
    val month: YearMonth,
    val startDay: Int,
    val incomeMinor: Long,
    val expensesMinor: Long,
    val currency: CurrencyCode,
    val configured: Boolean,
    val amountsHidden: Boolean,
) {
    val balanceMinor: Long get() = incomeMinor - expensesMinor
}
