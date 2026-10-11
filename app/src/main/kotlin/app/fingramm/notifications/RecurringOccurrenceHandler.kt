package app.fingramm.notifications

import app.fingramm.data.repo.RecurringRepository
import app.fingramm.domain.recurring.PendingOccurrence
import app.fingramm.domain.recurring.RecurringSchedule
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Действия кнопок уведомления («Подтвердить», «Отменить»). Работают с самым старым необработанным списанием
 * шаблона на момент нажатия, поэтому повторное нажатие или устаревшее уведомление ничего не ломают: если ждать
 * уже нечего, действие просто не выполняется.
 */
@Singleton
class RecurringOccurrenceHandler @Inject constructor(
    private val repository: RecurringRepository,
    private val clock: Clock,
) {
    /** Вносит операцию по шаблону. Возвращает true, если было что вносить. */
    suspend fun confirm(templateId: Long): Boolean {
        val occurrence = oldestPending(templateId) ?: return false
        repository.confirm(occurrence, System.currentTimeMillis())
        return true
    }

    /** Отменяет (пропускает) списание: операция не создаётся, шаблон сдвигается. Возвращает true, если было что. */
    suspend fun skip(templateId: Long): Boolean {
        val occurrence = oldestPending(templateId) ?: return false
        repository.skip(occurrence)
        return true
    }

    /** Самое старое наступившее и необработанное списание шаблона на сегодня. */
    suspend fun oldestPending(templateId: Long): PendingOccurrence? {
        val template = repository.get(templateId) ?: return null
        return RecurringSchedule.pending(listOf(template), LocalDate.now(clock)).firstOrNull()
    }
}
