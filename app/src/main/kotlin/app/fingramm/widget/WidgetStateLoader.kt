package app.fingramm.widget

import app.fingramm.data.repo.AppSettings
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.data.repo.StatsRepository
import app.fingramm.domain.model.BudgetProfile
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.period.BudgetPeriod
import app.fingramm.domain.stats.MonthCategoryTotal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Собирает [MonthWidgetState] из готовых агрегатов БД: ни одна операция целиком не читается. */
@Singleton
class WidgetStateLoader @Inject constructor(
    private val stats: StatsRepository,
    private val budget: BudgetRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /** Состояние при любом изменении операций, профиля или настроек. Месяц берётся на момент расчёта. */
    fun observe(): Flow<MonthWidgetState> =
        combine(stats.monthCategoryTotals, budget.profile, settings.settings) { rows, profile, current ->
            build(rows, profile, current)
        }.distinctUntilChanged()

    suspend fun load(): MonthWidgetState = observe().first()

    private fun build(
        rows: List<MonthCategoryTotal>,
        profile: BudgetProfile?,
        settings: AppSettings,
    ): MonthWidgetState {
        val month = BudgetPeriod.containing(LocalDate.now(clock), settings.monthStartDay)
        val current = rows.filter { it.month == month }
        return MonthWidgetState(
            month = month,
            startDay = settings.monthStartDay,
            incomeMinor = current.filter { it.type == TransactionType.INCOME }.sumOf { it.totalMinor },
            expensesMinor = current.filter { it.type == TransactionType.EXPENSE }.sumOf { it.totalMinor },
            currency = profile?.currency ?: CurrencyCode.RUB,
            configured = profile != null,
            amountsHidden = settings.lockEnabled,
        )
    }
}
