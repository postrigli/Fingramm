package app.fingramm.data.repo

import androidx.room.withTransaction
import app.fingramm.data.db.AppDatabase
import app.fingramm.data.toDomain
import app.fingramm.data.toEntity
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryLimit
import app.fingramm.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(private val db: AppDatabase) {
    private val dao = db.categoryDao()

    val categories: Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val limits: Flow<List<CategoryLimit>> = dao.observeLimits().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): Category? = dao.getById(id)?.toDomain()

    /** Сохраняет категорию (новую при id = 0) и её месячный лимит, если задан. */
    suspend fun save(category: Category, limitMinor: Long?): Long = db.withTransaction {
        val id = if (category.id == 0L) {
            dao.insert(category.toEntity())
        } else {
            dao.update(category.toEntity())
            category.id
        }
        if (limitMinor != null && limitMinor > 0 && category.type == TransactionType.EXPENSE) {
            dao.upsertLimit(CategoryLimit(id, limitMinor).toEntity())
        } else {
            dao.deleteLimit(id)
        }
        id
    }
}
