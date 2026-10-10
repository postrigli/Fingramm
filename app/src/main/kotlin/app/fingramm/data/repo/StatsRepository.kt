package app.fingramm.data.repo

import app.fingramm.data.db.AppDatabase
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.period.BudgetPeriod
import app.fingramm.domain.stats.MonthCategoryTotal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(db: AppDatabase, settings: SettingsRepository) {
    private val dao = db.transactionDao()

    /**
     * Агрегаты по расчётным месяцам и категориям — одна небольшая выборка вместо чтения всех операций.
     * Месяц зависит от настройки «День начала месяца»: при её изменении агрегат пересчитывается сам.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthCategoryTotals: Flow<List<MonthCategoryTotal>> = settings.monthStartDay.flatMapLatest { startDay ->
        dao.observeMonthCategoryTotals(BudgetPeriod.shiftDays(startDay)).map { rows ->
            rows.map { MonthCategoryTotal(YearMonth.parse(it.ym), it.categoryId, it.type, it.total) }
        }
    }

    /** Сколько раз категория использовалась — для сортировки быстрого выбора. */
    fun categoryUsage(type: TransactionType): Flow<Map<Long, Int>> =
        dao.observeCategoryUsage(type.name).map { rows -> rows.associate { it.categoryId to it.uses } }
}
