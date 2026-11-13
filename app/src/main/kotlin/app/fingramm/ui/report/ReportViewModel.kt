package app.fingramm.ui.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.data.repo.StatsRepository
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.period.BudgetPeriod
import app.fingramm.domain.report.MonthReport
import app.fingramm.domain.report.MonthReports
import app.fingramm.domain.report.ReportTiming
import app.fingramm.domain.stats.MonthTotals
import app.fingramm.domain.stats.StatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class ReportUiState(
    val loading: Boolean = true,
    val currency: CurrencyCode = CurrencyCode.RUB,
    val period: YearMonth = YearMonth.now(),
    val startDay: Int = BudgetPeriod.DEFAULT_START_DAY,
    val report: MonthReport = MonthReports.build(MonthTotals(YearMonth.now()), null),
    val categories: Map<Long, Category> = emptyMap(),
    /** Последний завершившийся период: дальше листать нельзя, текущий месяц ещё не закончен. */
    val lastClosed: YearMonth = YearMonth.now(),
) {
    val canGoNext: Boolean get() = period < lastClosed
}

/**
 * Отчёт месяца по фактическим операциям. Аргумент маршрута `period` (`2026-09`) необязателен: без него
 * открывается последний завершившийся период.
 */
@HiltViewModel
class ReportViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        budgetRepository: BudgetRepository,
        categoryRepository: CategoryRepository,
        statsRepository: StatsRepository,
        settings: SettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val requested: YearMonth? =
            savedStateHandle.get<String>("period")
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

        private val shift = MutableStateFlow(0)

        val state: StateFlow<ReportUiState> =
            combine(
                budgetRepository.profile,
                statsRepository.monthCategoryTotals,
                categoryRepository.categories,
                settings.monthStartDay,
                shift,
            ) { profile, rows, categories, startDay, back ->
                val byId = categories.associateBy { it.id }
                val totals = StatsCalculator.monthTotals(rows, byId)
                val lastClosed = ReportTiming.closedPeriod(LocalDate.now(clock), startDay)
                val period = (requested ?: lastClosed).minusMonths(back.toLong())
                ReportUiState(
                    loading = false,
                    currency = profile?.currency ?: CurrencyCode.RUB,
                    period = period,
                    startDay = startDay,
                    report = MonthReports.build(totals[period] ?: MonthTotals(period), totals[period.minusMonths(1)]),
                    categories = byId,
                    lastClosed = lastClosed,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReportUiState())

        fun previousPeriod() = shift.update { it + 1 }

        /** Вперёд можно листать лишь до последнего завершившегося периода: за этим следит [ReportUiState.canGoNext]. */
        fun nextPeriod() = shift.update { it - 1 }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
