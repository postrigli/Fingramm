package app.fingramm.ui.survey

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.repo.BudgetData
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.survey.SurveyDraft
import app.fingramm.domain.survey.SurveyMapper
import app.fingramm.domain.survey.SurveyStep
import app.fingramm.ui.onboarding.SurveyFormState
import app.fingramm.ui.onboarding.buildSurveyResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Сводка ответов опроса со ссылками на редактирование отдельных блоков. */
@HiltViewModel
class SurveySummaryViewModel @Inject constructor(private val repository: BudgetRepository) : ViewModel() {
    val budget: StateFlow<BudgetData?> =
        repository.budget.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun setCurrency(currency: CurrencyCode) {
        viewModelScope.launch { repository.setCurrency(currency) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Редактирование одного блока ответов; остальные ответы сохраняются как были, показатели пересчитываются. */
@HiltViewModel
class SurveyEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: BudgetRepository,
    private val clock: Clock,
) : ViewModel() {
    val step: SurveyStep = SurveyStep.valueOf(checkNotNull(savedStateHandle.get<String>("step")))

    private val _state = MutableStateFlow(SurveyFormState(step = step, loaded = false))
    val state: StateFlow<SurveyFormState> = _state.asStateFlow()

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    private var createdAt: LocalDate = LocalDate.now(clock)

    val today: LocalDate get() = LocalDate.now(clock)

    init {
        viewModelScope.launch {
            val budget = repository.getBudget() ?: return@launch
            createdAt = budget.profile.createdAt
            val draft = SurveyMapper.toDraft(
                budget.profile,
                budget.survey,
                budget.incomeSources,
                budget.assets,
                budget.debts,
            )
            _state.update { it.copy(draft = draft, loaded = true) }
        }
    }

    fun update(transform: (SurveyDraft) -> SurveyDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    fun save() {
        val current = _state.value
        if (current.errorsFor(step, today).isNotEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.updateSurvey(buildSurveyResult(context, current.draft, today, createdAt))
            _saved.send(Unit)
        }
    }
}
