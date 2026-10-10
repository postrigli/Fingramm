package app.fingramm.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.computePlan
import app.fingramm.data.repo.BudgetData
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.data.repo.GoalRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.data.repo.StatsRepository
import app.fingramm.domain.goals.GoalForecast
import app.fingramm.domain.goals.GoalForecasts
import app.fingramm.domain.metrics.CashFlow
import app.fingramm.domain.metrics.MetricsCalculator
import app.fingramm.domain.metrics.MetricsReport
import app.fingramm.domain.metrics.Snapshots
import app.fingramm.domain.metrics.VariableSource
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.Goal
import app.fingramm.domain.period.BudgetPeriod
import app.fingramm.domain.stats.MonthCategoryTotal
import app.fingramm.domain.stats.MonthTotals
import app.fingramm.domain.stats.StatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class DashboardMode { PLAN, FACT }

/** Сегмент структуры расходов. Для категорий [categoryId] и [name] заданы, для остальных — только [key]. */
enum class StructureKey { MANDATORY, VARIABLE, DEBT, FREE, CATEGORY, OTHERS }

data class StructureSlice(
    val key: StructureKey,
    val amountMinor: Long,
    val name: String? = null,
    val icon: String? = null,
)

data class GoalProgress(val goal: Goal, val forecast: GoalForecast)

data class DashboardUiState(
    val loading: Boolean = true,
    val currency: CurrencyCode = CurrencyCode.RUB,
    val mode: DashboardMode = DashboardMode.PLAN,
    val month: YearMonth = YearMonth.now(),
    val currentMonth: YearMonth = YearMonth.now(),
    /** День начала месяца из настроек: 1 — календарный месяц, иначе рядом с названием показываем даты периода. */
    val startDay: Int = BudgetPeriod.DEFAULT_START_DAY,
    val report: MetricsReport? = null,
    val structure: List<StructureSlice> = emptyList(),
    val monthlyBars: List<MonthTotals> = emptyList(),
    val goals: List<GoalProgress> = emptyList(),
    val variableSource: VariableSource = VariableSource.DECLARED,
    val debtBalanceMissing: Boolean = false,
    val factHasData: Boolean = false,
    val showDisclaimer: Boolean = false,
) {
    val canGoNext: Boolean get() = month < currentMonth
}

private data class Inputs(
    val budget: BudgetData?,
    val rows: List<MonthCategoryTotal>,
    val categories: List<Category>,
    val goals: List<Goal>,
    val disclaimerShown: Boolean,
    val startDay: Int,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    budgetRepository: BudgetRepository,
    statsRepository: StatsRepository,
    categoryRepository: CategoryRepository,
    goalRepository: GoalRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val mode = MutableStateFlow(DashboardMode.PLAN)

    /** На сколько расчётных месяцев назад от текущего открыт экран (0 — текущий). Сдвиг переживает смену дня начала. */
    private val monthsBack = MutableStateFlow(0)

    private val inputs = combine(
        budgetRepository.budget,
        statsRepository.monthCategoryTotals,
        categoryRepository.categories,
        goalRepository.goals,
        settings.settings,
    ) { budget, rows, categories, goals, appSettings ->
        Inputs(budget, rows, categories, goals, appSettings.disclaimerShown, appSettings.monthStartDay)
    }

    val state: StateFlow<DashboardUiState> = combine(inputs, mode, monthsBack) { inputs, mode, back ->
        compute(inputs, mode, back)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DashboardUiState())

    fun setMode(value: DashboardMode) {
        mode.value = value
    }

    fun previousMonth() = monthsBack.update { it + 1 }

    fun nextMonth() = monthsBack.update { (it - 1).coerceAtLeast(0) }

    fun dismissDisclaimer() {
        viewModelScope.launch { settings.setDisclaimerShown() }
    }

    private fun compute(inputs: Inputs, mode: DashboardMode, monthsBack: Int): DashboardUiState {
        val budget = inputs.budget ?: return DashboardUiState(loading = false)
        val today = LocalDate.now(clock)
        val current = BudgetPeriod.containing(today, inputs.startDay)
        val month = current.minusMonths(monthsBack.toLong())
        val categoriesById = inputs.categories.associateBy { it.id }
        val totals = StatsCalculator.monthTotals(inputs.rows, categoriesById)

        val plan = computePlan(budget, totals, current, inputs.startDay)

        val selected = totals[month] ?: MonthTotals(month)
        val (cashFlow, position) = when (mode) {
            DashboardMode.PLAN -> plan.cashFlow to plan.position
            DashboardMode.FACT -> Snapshots.fact(
                selected,
                plan.position,
                StatsCalculator.cumulativeBalanceUpTo(totals, month),
            )
        }
        val monthlyFlow = plan.cashFlow.freeCashFlowMinor
        return DashboardUiState(
            loading = false,
            currency = budget.profile.currency,
            mode = mode,
            month = month,
            currentMonth = current,
            startDay = inputs.startDay,
            report = MetricsCalculator.calculate(cashFlow, position),
            structure = when (mode) {
                DashboardMode.PLAN -> planStructure(plan.cashFlow)
                DashboardMode.FACT -> factStructure(selected, categoriesById)
            },
            monthlyBars = lastMonths(totals, if (mode == DashboardMode.FACT) month else current),
            goals = inputs.goals.sortedWith(compareBy({ it.horizon }, { it.id }))
                .map { GoalProgress(it, GoalForecasts.forecast(it, monthlyFlow, today)) }
                .filter { !it.forecast.reached }
                .take(MAX_GOALS),
            variableSource = plan.variableSource,
            debtBalanceMissing = plan.debtBalanceMissing,
            factHasData = selected.hasData,
            showDisclaimer = !inputs.disclaimerShown,
        )
    }

    private fun planStructure(cf: CashFlow): List<StructureSlice> = buildList {
        add(StructureSlice(StructureKey.MANDATORY, cf.mandatoryMinor))
        add(StructureSlice(StructureKey.VARIABLE, cf.variableMinor))
        add(StructureSlice(StructureKey.DEBT, cf.debtPaymentMinor))
        add(StructureSlice(StructureKey.FREE, cf.freeCashFlowMinor.coerceAtLeast(0)))
    }

    private fun factStructure(totals: MonthTotals, categories: Map<Long, Category>): List<StructureSlice> {
        val sorted = totals.expenseByCategory.entries.sortedByDescending { it.value }
        val top = sorted.take(MAX_CATEGORY_SLICES)
        val rest = sorted.drop(MAX_CATEGORY_SLICES).sumOf { it.value }
        return buildList {
            top.forEach { (id, amount) ->
                val category = categories[id]
                add(StructureSlice(StructureKey.CATEGORY, amount, category?.name, category?.icon))
            }
            if (rest > 0) add(StructureSlice(StructureKey.OTHERS, rest))
        }
    }

    private fun lastMonths(totals: Map<YearMonth, MonthTotals>, end: YearMonth): List<MonthTotals> =
        (BAR_MONTHS - 1 downTo 0).map { offset ->
            val m = end.minusMonths(offset.toLong())
            totals[m] ?: MonthTotals(m)
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_GOALS = 3
        const val MAX_CATEGORY_SLICES = 6
        const val BAR_MONTHS = 6
    }
}
