package app.fingramm.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.data.repo.StatsRepository
import app.fingramm.data.repo.TransactionFilter
import app.fingramm.data.repo.TransactionRepository
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryLimit
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.Transaction
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.money.Money
import app.fingramm.domain.period.BudgetPeriod
import app.fingramm.domain.stats.LimitUsage
import app.fingramm.domain.stats.MonthTotals
import app.fingramm.domain.stats.StatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

private const val PERCENT = 100
private const val MAX_ROWS = 1_000

data class CategoryStatsUiState(
    val loading: Boolean = true,
    val currency: CurrencyCode = CurrencyCode.RUB,
    val category: Category? = null,
    val month: YearMonth = YearMonth.now(),
    val currentMonth: YearMonth = YearMonth.now(),
    val startDay: Int = BudgetPeriod.DEFAULT_START_DAY,
    /** Сумма по категории за период и за предыдущий период. */
    val totalMinor: Long = 0,
    val previousMinor: Long = 0,
    /** Доля категории в расходах (или доходах) периода, процентов; null, если сравнивать не с чем. */
    val sharePercent: Int? = null,
    val limit: LimitUsage? = null,
    val transactions: List<Transaction> = emptyList(),
    val today: LocalDate = LocalDate.now(),
) {
    val canGoNext: Boolean get() = month < currentMonth
}

private data class StatsInputs(
    val category: Category?,
    val totals: Map<YearMonth, MonthTotals>,
    val limit: CategoryLimit?,
    val currency: CurrencyCode,
    val startDay: Int,
)

/**
 * Сводка по одной категории за расчётный месяц и список её операций. Открывается из диаграммы структуры расходов.
 * Аргументы маршрута: `id` категории и необязательный `period` (`2026-10`; без него — текущий период).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryStatsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        budgetRepository: BudgetRepository,
        categoryRepository: CategoryRepository,
        statsRepository: StatsRepository,
        settings: SettingsRepository,
        private val transactions: TransactionRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val categoryId: Long = savedStateHandle.get<Long>("id") ?: 0L

        /** Сдвиг от выбранного периода; null — период пришёл из маршрута и ещё не листался. */
        private val shift = MutableStateFlow(0)
        private val requested: YearMonth? =
            savedStateHandle.get<String>("period")
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

        private val inputs: Flow<StatsInputs> =
            combine(
                categoryRepository.categories,
                statsRepository.monthCategoryTotals,
                categoryRepository.limits,
                budgetRepository.profile,
                settings.monthStartDay,
            ) { categories, rows, limits, profile, startDay ->
                val byId = categories.associateBy { it.id }
                StatsInputs(
                    category = byId[categoryId],
                    totals = StatsCalculator.monthTotals(rows, byId),
                    limit = limits.firstOrNull { it.categoryId == categoryId },
                    currency = profile?.currency ?: CurrencyCode.RUB,
                    startDay = startDay,
                )
            }

        private val period: Flow<Pair<YearMonth, Int>> =
            combine(shift, inputs) { back, input ->
                val current = BudgetPeriod.containing(LocalDate.now(clock), input.startDay)
                (requested ?: current).minusMonths(back.toLong()) to input.startDay
            }

        private val rows: Flow<List<Transaction>> =
            combine(period, categoryRepository.categories) { p, categories ->
                Triple(p.first, p.second, categories)
            }.flatMapLatest { (month, startDay, categories) ->
                transactions.observe(
                    TransactionFilter(
                        from = BudgetPeriod.start(month, startDay),
                        to = BudgetPeriod.end(month, startDay),
                        categoryId = categoryId,
                    ),
                    categories,
                    MAX_ROWS,
                )
            }

        val state: StateFlow<CategoryStatsUiState> =
            combine(inputs, period, rows) { input, p, list ->
                val (month, startDay) = p
                val today = LocalDate.now(clock)
                val category = input.category
                val totals = input.totals[month] ?: MonthTotals(month)
                val previous = input.totals[month.minusMonths(1)] ?: MonthTotals(month.minusMonths(1))
                val isIncome = category?.type == TransactionType.INCOME

                fun amountOf(t: MonthTotals): Long {
                    val byCategory = if (isIncome) t.incomeByCategory else t.expenseByCategory
                    return byCategory[categoryId] ?: 0L
                }
                val spent = amountOf(totals)
                val whole = if (isIncome) totals.incomeMinor else totals.expensesMinor
                CategoryStatsUiState(
                    loading = false,
                    currency = input.currency,
                    category = category,
                    month = month,
                    currentMonth = BudgetPeriod.containing(today, startDay),
                    startDay = startDay,
                    totalMinor = spent,
                    previousMinor = amountOf(previous),
                    sharePercent = Money.ratio(spent, whole)?.let { Math.round(it * PERCENT).toInt() },
                    limit = input.limit?.takeIf { !isIncome }?.let { StatsCalculator.limitUsage(it, spent) },
                    transactions = list,
                    today = today,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CategoryStatsUiState())

        fun previousPeriod() = shift.update { it + 1 }

        /** Вперёд можно листать лишь до текущего периода: за этим следит [CategoryStatsUiState.canGoNext]. */
        fun nextPeriod() = shift.update { it - 1 }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
