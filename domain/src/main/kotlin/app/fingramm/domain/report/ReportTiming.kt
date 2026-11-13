package app.fingramm.domain.report

import app.fingramm.domain.period.BudgetPeriod
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Когда присылать «Отчёт за месяц готов» и за какой период. */
public object ReportTiming {
    public const val NOTIFY_HOUR: Int = 10

    /** Ближайшие 10:00 первого дня расчётного месяца строго после [now]. */
    public fun nextTrigger(now: LocalDateTime, startDay: Int): LocalDateTime {
        val period = BudgetPeriod.containing(now.toLocalDate(), startDay)
        val candidate = BudgetPeriod.start(period, startDay).atTime(NOTIFY_HOUR, 0)
        return if (candidate.isAfter(now)) {
            candidate
        } else {
            BudgetPeriod.start(period.plusMonths(1), startDay).atTime(NOTIFY_HOUR, 0)
        }
    }

    /** Последний завершившийся расчётный месяц: по нему строится отчёт. */
    public fun closedPeriod(today: LocalDate, startDay: Int): YearMonth =
        BudgetPeriod.containing(today, startDay).minusMonths(1)
}
