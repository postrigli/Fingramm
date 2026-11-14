package app.fingramm.ui.onboarding

import androidx.annotation.StringRes
import app.fingramm.R
import app.fingramm.domain.recurring.Recurrence

private const val MAX_DAY_OF_MONTH = 31
private const val DEFAULT_FIRST_DAY = 10
private const val DEFAULT_SECOND_DAY = 25
private const val DEFAULT_WEEKDAY = 5

/** Как часто приходит доход: раз в месяц, два раза в месяц (аванс и зарплата) или каждую неделю. */
enum class IncomeFrequency { MONTHLY, TWICE_MONTHLY, WEEKLY }

/** Вид дохода выбирает категорию операций: для второго дохода (вторая работа, аренда, подработка). */
enum class IncomeKind(
    val categoryKey: String,
    @StringRes val titleRes: Int,
) {
    SALARY("salary", R.string.income_kind_salary),
    RENT("rent_income", R.string.income_kind_rent),
    SIDE("side_income", R.string.income_kind_side),
}

/**
 * Регулярный доход на стартовом экране. [amountMinor] — сумма одной выплаты. Для «2 раза в месяц» создаются две
 * регулярные операции на [firstDay] и [secondDay], для «каждую неделю» — одна на день недели [weekday] (1 — ПН).
 */
data class IncomeRow(
    val kind: IncomeKind = IncomeKind.SALARY,
    val frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
    val enabled: Boolean = false,
    val amountMinor: Long? = null,
    val firstDay: Int? = DEFAULT_FIRST_DAY,
    val secondDay: Int? = DEFAULT_SECOND_DAY,
    val weekday: Int = DEFAULT_WEEKDAY,
) {
    val amountValid: Boolean get() = (amountMinor ?: 0L) > 0L
    val firstDayValid: Boolean get() = firstDay.isDayOfMonth()

    /** Второй день должен отличаться от первого: иначе две выплаты в один день сольются в одну. */
    val secondDayValid: Boolean get() = secondDay.isDayOfMonth() && secondDay != firstDay

    val scheduleValid: Boolean
        get() = when (frequency) {
            IncomeFrequency.MONTHLY -> firstDayValid
            IncomeFrequency.TWICE_MONTHLY -> firstDayValid && secondDayValid
            IncomeFrequency.WEEKLY -> true
        }

    /** Выключенный доход всегда допустим; включённому нужны сумма и расписание. */
    val valid: Boolean get() = !enabled || (amountValid && scheduleValid)

    /** Правила повтора регулярных операций, которые нужно создать для этого дохода. */
    fun recurrences(): List<Recurrence> = when (frequency) {
        IncomeFrequency.MONTHLY -> listOf(Recurrence.monthly(firstDay ?: DEFAULT_FIRST_DAY))
        IncomeFrequency.TWICE_MONTHLY ->
            listOfNotNull(firstDay, secondDay).distinct().sorted().map { Recurrence.monthly(it) }
        IncomeFrequency.WEEKLY -> listOf(Recurrence.weekly(setOf(weekday)))
    }
}

private fun Int?.isDayOfMonth(): Boolean = this != null && this in 1..MAX_DAY_OF_MONTH
