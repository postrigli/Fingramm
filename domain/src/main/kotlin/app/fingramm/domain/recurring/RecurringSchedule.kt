package app.fingramm.domain.recurring

import app.fingramm.domain.model.RecurrenceFrequency
import app.fingramm.domain.model.RecurringTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** Ожидающее подтверждения списание по шаблону. */
public data class PendingOccurrence(val template: RecurringTemplate, val dueDate: LocalDate)

/** Правило повторения: раз в месяц в [dayOfMonth] или по [weekdays] (ISO 1–7). */
public data class Recurrence(
    val frequency: RecurrenceFrequency,
    val dayOfMonth: Int = 1,
    val weekdays: Set<Int> = emptySet(),
) {
    public companion object {
        public fun monthly(dayOfMonth: Int): Recurrence = Recurrence(RecurrenceFrequency.MONTHLY, dayOfMonth)

        public fun weekly(weekdays: Set<Int>): Recurrence = Recurrence(RecurrenceFrequency.WEEKLY, weekdays = weekdays)
    }
}

public val RecurringTemplate.recurrence: Recurrence
    get() = Recurrence(frequency, dayOfMonth, weekdays)

/**
 * Расписание регулярных операций: ежемесячно в выбранный день (29–31 в коротких месяцах сдвигается на последний
 * день) или еженедельно по выбранным дням недели. Ожидающие операции вычисляются, а не хранятся: достаточно
 * `nextDueDate` в шаблоне.
 */
public object RecurringSchedule {
    public const val MAX_PENDING_PER_TEMPLATE: Int = 12
    private const val DAYS_IN_WEEK = 7
    private const val MINUTES_IN_HOUR = 60

    public fun occurrence(month: YearMonth, dayOfMonth: Int): LocalDate =
        month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

    /** Первая дата по правилу, не раньше [from]. */
    public fun firstDueOnOrAfter(from: LocalDate, rule: Recurrence): LocalDate = when (rule.frequency) {
        RecurrenceFrequency.MONTHLY -> {
            val thisMonth = occurrence(YearMonth.from(from), rule.dayOfMonth)
            if (thisMonth.isBefore(from)) occurrence(YearMonth.from(from).plusMonths(1), rule.dayOfMonth) else thisMonth
        }
        RecurrenceFrequency.WEEKLY -> firstWeekdayOnOrAfter(from, rule.weekdays) ?: from
    }

    /** Следующая дата по правилу строго после [due]. */
    public fun nextAfter(due: LocalDate, rule: Recurrence): LocalDate = when (rule.frequency) {
        RecurrenceFrequency.MONTHLY -> occurrence(YearMonth.from(due).plusMonths(1), rule.dayOfMonth)
        RecurrenceFrequency.WEEKLY ->
            // Пустой набор дней недопустим, но сбоя быть не должно: такой шаблон повторяется раз в неделю.
            firstWeekdayOnOrAfter(due.plusDays(1), rule.weekdays) ?: due.plusDays(DAYS_IN_WEEK.toLong())
    }

    public fun firstDueOnOrAfter(from: LocalDate, dayOfMonth: Int): LocalDate =
        firstDueOnOrAfter(from, Recurrence.monthly(dayOfMonth))

    public fun nextAfter(due: LocalDate, dayOfMonth: Int): LocalDate = nextAfter(due, Recurrence.monthly(dayOfMonth))

    private fun firstWeekdayOnOrAfter(from: LocalDate, weekdays: Set<Int>): LocalDate? {
        val valid = weekdays.filter { it in DayOfWeek.MONDAY.value..DayOfWeek.SUNDAY.value }.toSet()
        if (valid.isEmpty()) return null
        return (0 until DAYS_IN_WEEK).map { from.plusDays(it.toLong()) }.first { it.dayOfWeek.value in valid }
    }

    /** Все наступившие и ещё не обработанные списания по активным шаблонам, от старых к новым. */
    public fun pending(templates: List<RecurringTemplate>, today: LocalDate): List<PendingOccurrence> =
        templates.filter { it.active }.flatMap { template ->
            val result = mutableListOf<PendingOccurrence>()
            var due = template.nextDueDate
            while (!due.isAfter(today) && result.size < MAX_PENDING_PER_TEMPLATE) {
                result += PendingOccurrence(template, due)
                due = nextAfter(due, template.recurrence)
            }
            result
        }.sortedWith(compareBy({ it.dueDate }, { it.template.id }))

    /**
     * Ближайший момент уведомления строго после [now]: дата по правилу и время шаблона. Null, если шаблон
     * приостановлен или уведомление не задано.
     */
    public fun nextNotification(template: RecurringTemplate, now: LocalDateTime): LocalDateTime? {
        val minute = template.notifyAtMinute ?: return null
        if (!template.active) return null
        val time = LocalTime.of(minute / MINUTES_IN_HOUR, minute % MINUTES_IN_HOUR)
        val rule = template.recurrence
        val first = firstDueOnOrAfter(now.toLocalDate(), rule)
        val candidate = first.atTime(time)
        return if (candidate.isAfter(now)) candidate else nextAfter(first, rule).atTime(time)
    }
}
