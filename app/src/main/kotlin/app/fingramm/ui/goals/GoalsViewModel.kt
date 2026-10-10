package app.fingramm.ui.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.computePlan
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.data.repo.GoalRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.data.repo.StatsRepository
import app.fingramm.domain.goals.GoalForecast
import app.fingramm.domain.goals.GoalForecasts
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.Goal
import app.fingramm.domain.model.GoalHorizon
import app.fingramm.domain.period.BudgetPeriod
import app.fingramm.domain.stats.StatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class GoalItem(val goal: Goal, val forecast: GoalForecast)

data class GoalsUiState(
    val loading: Boolean = true,
    val currency: CurrencyCode = CurrencyCode.RUB,
    val monthlyFlowMinor: Long = 0,
    val year: List<GoalItem> = emptyList(),
    val mid: List<GoalItem> = emptyList(),
    val today: LocalDate = LocalDate.now(),
) {
    val isEmpty: Boolean get() = year.isEmpty() && mid.isEmpty()
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: GoalRepository,
    budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    statsRepository: StatsRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<GoalsUiState> = combine(
        repository.goals,
        budgetRepository.budget,
        statsRepository.monthCategoryTotals,
        categoryRepository.categories,
        settings.monthStartDay,
    ) { goals, budget, rows, categories, startDay ->
        val today = LocalDate.now(clock)
        val flow = budget?.let {
            val totals = StatsCalculator.monthTotals(rows, categories.associateBy { c -> c.id })
            val current = BudgetPeriod.containing(today, startDay)
            computePlan(it, totals, current, startDay).cashFlow.freeCashFlowMinor
        } ?: 0L
        val items = goals.map { GoalItem(it, GoalForecasts.forecast(it, flow, today)) }
        GoalsUiState(
            loading = false,
            currency = budget?.profile?.currency ?: CurrencyCode.RUB,
            monthlyFlowMinor = flow,
            year = items.filter { it.goal.horizon == GoalHorizon.YEAR },
            mid = items.filter { it.goal.horizon == GoalHorizon.MID },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GoalsUiState())

    fun addToSaved(goalId: Long, amountMinor: Long) {
        viewModelScope.launch { repository.addToSaved(goalId, amountMinor) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class GoalFormState(
    val loaded: Boolean = false,
    val editingId: Long? = null,
    val title: String = "",
    val targetMinor: Long? = null,
    val savedMinor: Long? = null,
    val deadline: LocalDate? = null,
    val horizon: GoalHorizon = GoalHorizon.YEAR,
    val showErrors: Boolean = false,
) {
    val titleError: Boolean get() = showErrors && title.isBlank()
    val targetError: Boolean get() = showErrors && targetMinor != null && targetMinor <= 0
    val deadlineError: Boolean get() = false
}

@HiltViewModel
class GoalEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GoalRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val _state = MutableStateFlow(GoalFormState(editingId = id))
    val state: StateFlow<GoalFormState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = _done.receiveAsFlow()

    private var original: Goal? = null

    init {
        viewModelScope.launch {
            val goal = id?.let { repository.get(it) }
            original = goal
            _state.update {
                if (goal == null) {
                    it.copy(loaded = true)
                } else {
                    it.copy(
                        loaded = true,
                        title = goal.title,
                        targetMinor = goal.targetMinor,
                        savedMinor = goal.savedMinor,
                        deadline = goal.deadline,
                        horizon = goal.horizon,
                    )
                }
            }
        }
    }

    fun update(transform: (GoalFormState) -> GoalFormState) = _state.update(transform)

    fun save() {
        val s = _state.value
        if (s.title.isBlank() || (s.targetMinor != null && s.targetMinor <= 0)) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            repository.save(
                Goal(
                    id = original?.id ?: 0,
                    title = s.title.trim(),
                    targetMinor = s.targetMinor,
                    savedMinor = s.savedMinor ?: 0,
                    deadline = s.deadline,
                    horizon = s.horizon,
                    createdAt = original?.createdAt ?: LocalDate.now(clock),
                ),
            )
            _done.send(Unit)
        }
    }

    fun delete() {
        val goalId = original?.id ?: return
        viewModelScope.launch {
            repository.delete(goalId)
            _done.send(Unit)
        }
    }
}
