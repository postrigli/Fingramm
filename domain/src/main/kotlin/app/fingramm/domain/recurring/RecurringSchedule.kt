package app.fingramm.domain.recurring

import app.fingramm.domain.model.RecurringTemplate
import java.time.LocalDate
import java.time.YearMonth

/** Ожидающее подтверждения списание по шаблону. */
public data class PendingOccurrence(val template: RecurringTemplate, val dueDate: LocalDate)

/**
 * Расписание ежемесячных шаблонов. День месяца 29–31 в коротких месяцах сдвигается на последний день.
 * Ожидающие операции вычисляются, а не хранятся: достаточно `nextDueDate` в шаблоне.
 */
public object RecurringSchedule {
    public const val MAX_PENDING_PER_TEMPLATE: Int = 12

    public fun occurrence(month: YearMonth, dayOfMonth: Int): LocalDate =
        month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

    /** Первая дата с нужным днём месяца, не раньше [from]. */
    public fun firstDueOnOrAfter(from: LocalDate, dayOfMonth: Int): LocalDate {
        val thisMonth = occurrence(YearMonth.from(from), dayOfMonth)
        return if (thisMonth.isBefore(from)) occurrence(YearMonth.from(from).plusMonths(1), dayOfMonth) else thisMonth
    }

    public fun nextAfter(due: LocalDate, dayOfMonth: Int): LocalDate =
        occurrence(YearMonth.from(due).plusMonths(1), dayOfMonth)

    /** Все наступившие и ещё не обработанные списания по активным шаблонам, от старых к новым. */
    public fun pending(templates: List<RecurringTemplate>, today: LocalDate): List<PendingOccurrence> =
        templates.filter { it.active }.flatMap { template ->
            val result = mutableListOf<PendingOccurrence>()
            var due = template.nextDueDate
            while (!due.isAfter(today) && result.size < MAX_PENDING_PER_TEMPLATE) {
                result += PendingOccurrence(template, due)
                due = nextAfter(due, template.dayOfMonth)
            }
            result
        }.sortedWith(compareBy({ it.dueDate }, { it.template.id }))
}
