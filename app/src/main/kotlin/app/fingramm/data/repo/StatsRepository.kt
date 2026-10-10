package app.fingramm.data.repo

import app.fingramm.data.db.AppDatabase
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.stats.MonthCategoryTotal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(db: AppDatabase) {
    private val dao = db.transactionDao()

    /** Агрегаты по месяцам и категориям — одна небольшая выборка вместо чтения всех операций. */
    val monthCategoryTotals: Flow<List<MonthCategoryTotal>> = dao.observeMonthCategoryTotals().map { rows ->
        rows.map { MonthCategoryTotal(YearMonth.parse(it.ym), it.categoryId, it.type, it.total) }
    }

    /** Сколько раз категория использовалась — для сортировки быстрого выбора. */
    fun categoryUsage(type: TransactionType): Flow<Map<Long, Int>> =
        dao.observeCategoryUsage(type.name).map { rows -> rows.associate { it.categoryId to it.uses } }
}
