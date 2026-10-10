package app.fingramm.data.repo

import app.fingramm.data.db.AppDatabase
import app.fingramm.data.toDomain
import app.fingramm.data.toEntity
import app.fingramm.domain.model.Goal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(db: AppDatabase) {
    private val dao = db.goalDao()

    val goals: Flow<List<Goal>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): Goal? = dao.getById(id)?.toDomain()

    suspend fun save(goal: Goal): Long =
        if (goal.id == 0L) {
            dao.insert(goal.toEntity())
        } else {
            dao.update(goal.toEntity())
            goal.id
        }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Пополнение вручную. Накопленная сумма не опускается ниже нуля. */
    suspend fun addToSaved(id: Long, deltaMinor: Long) {
        val goal = dao.getById(id) ?: return
        dao.update(goal.copy(savedMinor = (goal.savedMinor + deltaMinor).coerceAtLeast(0)))
    }
}
