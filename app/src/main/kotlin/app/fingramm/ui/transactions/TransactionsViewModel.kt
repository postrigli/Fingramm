package app.fingramm.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.computePlan
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.data.repo.RecurringRepository
import app.fingramm.data.repo.StatsRepository
import app.fingramm.data.repo.TransactionFilter
import app.fingramm.data.repo.TransactionRepository
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.Transaction
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.recurring.PendingOccurrence
import app.fingramm.domain.recurring.RecurringSchedule
import app.fingramm.domain.stats.LimitUsage
import app.fingramm.domain.stats.MonthTotals
import app.fingramm.domain.stats.StatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class PeriodPreset { THIS_MONTH, LAST_MONTH, ALL, CUSTOM }

/** Параметры списка, выбираемые пользователем. */
data class ListFilters(
    val period: PeriodPreset = PeriodPreset.THIS_MONTH,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val type: TransactionType? = null,
    val categoryId: Long? = null,
    val query: String = "",
    val limit: Int = PAGE_SIZE,
) {
    companion object {
        const val PAGE_SIZE = 100
    }
}

data class DayGroup(val date: LocalDate, val netMinor: Long, val items: List<Transaction>)

data class LimitRow(val category: Category, val usage: LimitUsage)

data class MonthOverview(
    val month: YearMonth,
    val totals: MonthTotals,
    val planIncomeMinor: Long,
    val planExpensesMinor: Long,
    val limits: List<LimitRow>,
)

data class TransactionsUiState(
    val loading: Boolean = true,
    val currency: CurrencyCode = CurrencyCode.RUB,
    val filters: ListFilters = ListFilters(),
    val groups: List<DayGroup> = emptyList(),
    val hasMore: Boolean = false,
    val categories: List<Category> = emptyList(),
    val overview: MonthOverview? = null,
    val pending: List<PendingOccurrence> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val currentMonth: YearMonth = YearMonth.now(),
) {
    val categoriesById: Map<Long, Category> get() = categories.associateBy { it.id }
    val isFiltered: Boolean
        get() = filters.type != null || filters.categoryId != null || filters.query.isNotBlank() ||
            filters.period != PeriodPreset.THIS_MONTH
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val recurring: RecurringRepository,
    budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    statsRepository: StatsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val filters = MutableStateFlow(ListFilters())
    private val overviewMonth = MutableStateFlow(YearMonth.now(clock))

    private val _snackbar = Channel<Transaction>(Channel.BUFFERED)

    /** Удалённая операция — экран показывает «Отменить». */
    val deleted: Flow<Transaction> = _snackbar.receiveAsFlow()

    private val items: Flow<List<Transaction>> = combine(filters, categoryRepository.categories) { f, categories ->
        f to categories
    }.flatMapLatest { (f, categories) ->
        val (from, to) = periodRange(f)
        transactions.observe(
            TransactionFilter(from, to, f.type, f.categoryId, f.query),
            categories,
            f.limit,
        )
    }

    private val overview: Flow<MonthOverview?> = combine(
        budgetRepository.budget,
        statsRepository.monthCategoryTotals,
        categoryRepository.categories,
        categoryRepository.limits,
        overviewMonth,
    ) { budget, rows, categories, limits, month ->
        budget ?: return@combine null
        val byId = categories.associateBy { it.id }
        val totals = StatsCalculator.monthTotals(rows, byId)
        val selected = totals[month] ?: MonthTotals(month)
        val plan = computePlan(budget, totals, YearMonth.now(clock))
        MonthOverview(
            month = month,
            totals = selected,
            planIncomeMinor = plan.cashFlow.incomeMinor,
            planExpensesMinor = plan.cashFlow.outflowMinor,
            limits = limits.mapNotNull { limit ->
                val category = byId[limit.categoryId] ?: return@mapNotNull null
                val spent = selected.expenseByCategory[limit.categoryId] ?: 0L
                LimitRow(category, StatsCalculator.limitUsage(limit, spent))
            }.sortedByDescending { it.usage.ratio },
        )
    }

    private val currency: Flow<CurrencyCode> = budgetRepository.profile.map { it?.currency ?: CurrencyCode.RUB }

    val state: StateFlow<TransactionsUiState> = combine(
        combine(
            filters,
            items,
            categoryRepository.categories,
        ) { f, list, categories -> Triple(f, list, categories) },
        overview,
        recurring.templates,
        currency,
    ) { (f, list, categories), overview, templates, currency ->
        val today = LocalDate.now(clock)
        TransactionsUiState(
            loading = false,
            currency = currency,
            filters = f,
            groups = list.groupBy { it.date }.map { (date, dayItems) -> DayGroup(date, netOf(dayItems), dayItems) },
            hasMore = list.size >= f.limit,
            categories = categories,
            overview = overview,
            pending = RecurringSchedule.pending(templates, today),
            today = today,
            currentMonth = YearMonth.now(clock),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TransactionsUiState())

    fun setPeriod(preset: PeriodPreset) = filters.update { it.copy(period = preset, limit = ListFilters.PAGE_SIZE) }

    fun setCustomPeriod(from: LocalDate, to: LocalDate) = filters.update {
        it.copy(period = PeriodPreset.CUSTOM, customFrom = from, customTo = to, limit = ListFilters.PAGE_SIZE)
    }

    fun setType(type: TransactionType?) = filters.update { it.copy(type = type, limit = ListFilters.PAGE_SIZE) }

    fun setCategory(categoryId: Long?) = filters.update {
        it.copy(
            categoryId = categoryId,
            limit = ListFilters.PAGE_SIZE,
        )
    }

    fun setQuery(query: String) = filters.update { it.copy(query = query, limit = ListFilters.PAGE_SIZE) }

    fun loadMore() = filters.update { it.copy(limit = it.limit + ListFilters.PAGE_SIZE) }

    fun previousOverviewMonth() = overviewMonth.update { it.minusMonths(1) }

    fun nextOverviewMonth() = overviewMonth.update { if (it < YearMonth.now(clock)) it.plusMonths(1) else it }

    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            transactions.delete(transaction.id)
            _snackbar.send(transaction)
        }
    }

    fun undoDelete(transaction: Transaction) {
        viewModelScope.launch { transactions.restore(transaction) }
    }

    fun confirmPending(occurrence: PendingOccurrence) {
        viewModelScope.launch { recurring.confirm(occurrence, System.currentTimeMillis()) }
    }

    fun skipPending(occurrence: PendingOccurrence) {
        viewModelScope.launch { recurring.skip(occurrence) }
    }

    private fun netOf(items: List<Transaction>): Long =
        items.sumOf { if (it.type == TransactionType.INCOME) it.amountMinor else -it.amountMinor }

    private fun periodRange(f: ListFilters): Pair<LocalDate?, LocalDate?> {
        val now = YearMonth.now(clock)
        return when (f.period) {
            PeriodPreset.THIS_MONTH -> now.atDay(1) to now.atEndOfMonth()
            PeriodPreset.LAST_MONTH -> now.minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
            PeriodPreset.ALL -> null to null
            PeriodPreset.CUSTOM -> f.customFrom to f.customTo
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
