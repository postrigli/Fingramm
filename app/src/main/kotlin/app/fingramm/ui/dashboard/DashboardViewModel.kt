package app.fingramm.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.data.repo.GoalRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.data.repo.StatsRepository
import app.fingramm.domain.goals.GoalForecast
import app.fingramm.domain.goals.GoalForecasts
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

/**
 * Сегмент структуры расходов: категория ([categoryId] задан) или «Остальное» (null — объединённый хвост,
 * на него нажимать нельзя).
 */
data class StructureSlice(
    val amountMinor: Long,
    val categoryId: Long? = null,
    val name: String? = null,
    val icon: String? = null,
)

data class GoalProgress(val goal: Goal, val forecast: GoalForecast)

data class DashboardUiState(
    val loading: Boolean = true,
    val currency: CurrencyCode = CurrencyCode.RUB,
    val month: YearMonth = YearMonth.now(),
    val currentMonth: YearMonth = YearMonth.now(),
    /** День начала месяца из настроек: 1 — календарный месяц, иначе рядом с названием показываем даты периода. */
    val startDay: Int = BudgetPeriod.DEFAULT_START_DAY,
    val totals: MonthTotals = MonthTotals(YearMonth.now()),
    val structure: List<StructureSlice> = emptyList(),
    val monthlyBars: List<MonthTotals> = emptyList(),
    val goals: List<GoalProgress> = emptyList(),
    val showDisclaimer: Boolean = false,
    /** Сколько денег сейчас: начальный остаток плюс все доходы минус все расходы. */
    val balanceMinor: Long = 0,
) {
    val canGoNext: Boolean get() = month < currentMonth
}

private data class Inputs(
    val currency: CurrencyCode,
    val rows: List<MonthCategoryTotal>,
    val categories: List<Category>,
    val goals: List<Goal>,
    val disclaimerShown: Boolean,
    val openingBalanceMinor: Long,
    val startDay: Int,
)

@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        budgetRepository: BudgetRepository,
        statsRepository: StatsRepository,
        categoryRepository: CategoryRepository,
        goalRepository: GoalRepository,
        private val settings: SettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        /** На сколько расчётных месяцев назад от текущего открыт экран (0 — текущий). Переживает смену дня начала. */
        private val monthsBack = MutableStateFlow(0)

        private val inputs =
            combine(
                budgetRepository.profile,
                statsRepository.monthCategoryTotals,
                categoryRepository.categories,
                goalRepository.goals,
                settings.settings,
            ) { profile, rows, categories, goals, appSettings ->
                Inputs(
                    profile?.currency ?: CurrencyCode.RUB,
                    rows,
                    categories,
                    goals,
                    appSettings.disclaimerShown,
                    appSettings.openingBalanceMinor,
                    appSettings.monthStartDay,
                )
            }

        val state: StateFlow<DashboardUiState> =
            combine(inputs, monthsBack) { inputs, back -> compute(inputs, back) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DashboardUiState())

        fun previousMonth() = monthsBack.update { it + 1 }

        fun nextMonth() = monthsBack.update { (it - 1).coerceAtLeast(0) }

        fun dismissDisclaimer() {
            viewModelScope.launch { settings.setDisclaimerShown() }
        }

        private fun compute(
            inputs: Inputs,
            monthsBack: Int,
        ): DashboardUiState {
            val today = LocalDate.now(clock)
            val current = BudgetPeriod.containing(today, inputs.startDay)
            val month = current.minusMonths(monthsBack.toLong())
            val categoriesById = inputs.categories.associateBy { it.id }
            val totals = StatsCalculator.monthTotals(inputs.rows, categoriesById)
            val selected = totals[month] ?: MonthTotals(month)
            val monthlyFlow = StatsCalculator.averageMonthlyFlow(totals, current) ?: 0L
            return DashboardUiState(
                loading = false,
                currency = inputs.currency,
                month = month,
                currentMonth = current,
                startDay = inputs.startDay,
                totals = selected,
                structure = structure(selected, categoriesById),
                monthlyBars = lastMonths(totals, month),
                goals =
                    inputs.goals.sortedWith(compareBy({ it.horizon }, { it.id }))
                        .map { GoalProgress(it, GoalForecasts.forecast(it, monthlyFlow, today)) }
                        .filter { !it.forecast.reached }
                        .take(MAX_GOALS),
                showDisclaimer = !inputs.disclaimerShown,
                balanceMinor = StatsCalculator.currentBalance(inputs.openingBalanceMinor, totals),
            )
        }

        private fun structure(
            totals: MonthTotals,
            categories: Map<Long, Category>,
        ): List<StructureSlice> {
            val sorted = totals.expenseByCategory.entries.sortedByDescending { it.value }
            val top = sorted.take(MAX_CATEGORY_SLICES)
            val rest = sorted.drop(MAX_CATEGORY_SLICES).sumOf { it.value }
            return buildList {
                top.forEach { (id, amount) ->
                    val category = categories[id]
                    add(StructureSlice(amount, id, category?.name, category?.icon))
                }
                if (rest > 0) add(StructureSlice(rest))
            }
        }

        private fun lastMonths(
            totals: Map<YearMonth, MonthTotals>,
            end: YearMonth,
        ): List<MonthTotals> =
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
