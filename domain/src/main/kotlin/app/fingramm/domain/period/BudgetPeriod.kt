package app.fingramm.domain.period

import java.time.LocalDate
import java.time.YearMonth

/**
 * Расчётный «месяц» бюджета: календарный (с 1 числа) или начинающийся с выбранного дня 1–28.
 *
 * Период обозначается [YearMonth] месяца, **в котором он начался**: при начале с 25 числа период «октябрь» — это
 * 25 октября – 24 ноября. Так весь код, который уже работает с `YearMonth` (итоги, графики, сравнение), не меняется:
 * меняется только то, какие даты входят в «месяц». День начала не больше 28, чтобы период начинался в любом месяце.
 *
 * Определение через сдвиг: дата принадлежит периоду месяца, в который она попадает после сдвига на `день − 1` назад.
 * Тот же сдвиг применяется в SQL-агрегате (см. [shiftDays]), поэтому БД и код всегда согласованы.
 */
public object BudgetPeriod {
    public const val DEFAULT_START_DAY: Int = 1
    public const val MAX_START_DAY: Int = 28

    /** Приводит день к допустимому диапазону 1–28 (значения из файла или настроек не доверяем). */
    public fun normalize(startDay: Int): Int = startDay.coerceIn(DEFAULT_START_DAY, MAX_START_DAY)

    /** На сколько дней назад сдвигается дата, чтобы взять месяц периода: 0 для календарного месяца. */
    public fun shiftDays(startDay: Int): Int = normalize(startDay) - 1

    /** Период, которому принадлежит [date]. */
    public fun containing(date: LocalDate, startDay: Int): YearMonth =
        YearMonth.from(date.minusDays(shiftDays(startDay).toLong()))

    /** Первый день периода. */
    public fun start(period: YearMonth, startDay: Int): LocalDate = period.atDay(normalize(startDay))

    /** Последний день периода (включительно): день перед началом следующего. */
    public fun end(period: YearMonth, startDay: Int): LocalDate = start(period.plusMonths(1), startDay).minusDays(1)
}
