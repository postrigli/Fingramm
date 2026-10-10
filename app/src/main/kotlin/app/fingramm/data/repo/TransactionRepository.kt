package app.fingramm.data.repo

import androidx.room.withTransaction
import app.fingramm.data.db.AppDatabase
import app.fingramm.data.toDomain
import app.fingramm.data.toEntity
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.Transaction
import app.fingramm.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Фильтры списка операций. Null / пустая строка — «без ограничения». */
data class TransactionFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val type: TransactionType? = null,
    val categoryId: Long? = null,
    val query: String = "",
)

@Singleton
class TransactionRepository @Inject constructor(private val db: AppDatabase) {
    private val dao = db.transactionDao()

    /**
     * Операции по фильтру, новые сверху, не более [limit]. Поиск идёт по комментарию и по названию категории
     * ([categories] нужны, чтобы найти категории по подстроке: SQLite не умеет регистронезависимый LIKE для кириллицы).
     */
    fun observe(filter: TransactionFilter, categories: List<Category>, limit: Int): Flow<List<Transaction>> {
        val query = filter.query.trim().lowercase()
        val matching = if (query.isEmpty()) {
            emptyList()
        } else {
            categories.filter { it.name.lowercase().contains(query) }.map { it.id }
        }
        return dao.observeFiltered(
            fromDate = filter.from?.toString(),
            toDate = filter.to?.toString(),
            type = filter.type?.name,
            categoryId = filter.categoryId,
            query = escapeLike(query),
            matchingCategoryIds = matching,
            limit = limit,
        ).map { list -> list.map { it.toDomain() } }
    }

    suspend fun get(id: Long): Transaction? = dao.getById(id)?.toDomain()

    /** Добавляет операцию; [Transaction.createdAt] проставляется, если не задан. */
    suspend fun add(transaction: Transaction, nowMillis: Long): Long {
        val createdAt = transaction.createdAt.takeIf { it > 0 } ?: nowMillis
        return dao.insert(transaction.copy(id = 0, createdAt = createdAt).toEntity())
    }

    suspend fun update(transaction: Transaction) = dao.update(transaction.toEntity())

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Возврат удалённой операции (отмена свайпа) с прежним id. */
    suspend fun restore(transaction: Transaction) {
        dao.insert(transaction.toEntity())
    }

    /** Сколько операций внесено с указанного момента — для умных напоминаний. */
    suspend fun countCreatedSince(sinceMillis: Long): Int = dao.countCreatedSince(sinceMillis)

    suspend fun addAll(transactions: List<Transaction>) = db.withTransaction {
        dao.insertAll(transactions.map { it.toEntity() })
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
