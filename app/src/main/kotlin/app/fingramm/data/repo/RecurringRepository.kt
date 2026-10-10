package app.fingramm.data.repo

import androidx.room.withTransaction
import app.fingramm.data.db.AppDatabase
import app.fingramm.data.toDomain
import app.fingramm.data.toEntity
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.Transaction
import app.fingramm.domain.recurring.PendingOccurrence
import app.fingramm.domain.recurring.RecurringSchedule
import app.fingramm.domain.recurring.recurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringRepository @Inject constructor(private val db: AppDatabase) {
    private val dao = db.recurringDao()
    private val transactions = db.transactionDao()

    val templates: Flow<List<RecurringTemplate>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): RecurringTemplate? = dao.getById(id)?.toDomain()

    suspend fun save(template: RecurringTemplate): Long =
        if (template.id == 0L) {
            dao.insert(template.toEntity())
        } else {
            dao.update(template.toEntity())
            template.id
        }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Подтверждение ожидающего списания: создаёт операцию и сдвигает шаблон на следующую дату по его правилу. */
    suspend fun confirm(occurrence: PendingOccurrence, nowMillis: Long) {
        db.withTransaction {
            val template = dao.getById(occurrence.template.id) ?: return@withTransaction
            // Повторное нажатие или устаревший список: обрабатываем только ближайшее необработанное списание.
            if (template.nextDueDate != occurrence.dueDate) return@withTransaction
            val entity = Transaction(
                type = template.type,
                amountMinor = template.amountMinor,
                categoryId = template.categoryId,
                date = occurrence.dueDate,
                note = template.note,
                accountType = template.accountType,
                recurringId = template.id,
                createdAt = nowMillis,
            ).toEntity()
            transactions.insert(entity)
            advance(template.id, occurrence.dueDate)
        }
    }

    /** Пропуск списания без создания операции. */
    suspend fun skip(occurrence: PendingOccurrence) {
        db.withTransaction { advance(occurrence.template.id, occurrence.dueDate) }
    }

    private suspend fun advance(templateId: Long, handledDue: LocalDate) {
        val template = dao.getById(templateId) ?: return
        // Шаблон сдвигается только если обработана именно ближайшая дата (защита от двойного нажатия).
        if (template.nextDueDate == handledDue) {
            val next = RecurringSchedule.nextAfter(handledDue, template.toDomain().recurrence)
            dao.update(template.copy(nextDueDate = next))
        }
    }
}
