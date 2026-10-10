package app.fingramm.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.domain.model.ReminderSettings
import app.fingramm.domain.survey.ReminderDefaults
import app.fingramm.domain.survey.SurveyDraft
import app.fingramm.domain.survey.SurveyWizard
import app.fingramm.domain.survey.WizardAdvance
import app.fingramm.domain.survey.WizardState
import app.fingramm.notifications.ReminderScheduler
import app.fingramm.ui.CategoryPresets
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class OnboardingUiState(val wizard: WizardState = WizardState(), val saving: Boolean = false)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgetRepository: BudgetRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _events = Channel<Unit>(Channel.BUFFERED)

    /** Срабатывает один раз, когда опрос сохранён и можно открывать дашборд. */
    val finished = _events.receiveAsFlow()

    val today: LocalDate get() = LocalDate.now(clock)

    fun update(transform: (SurveyDraft) -> SurveyDraft) {
        _state.update { it.copy(wizard = it.wizard.copy(draft = transform(it.wizard.draft))) }
    }

    fun back() = _state.update { it.copy(wizard = SurveyWizard.back(it.wizard)) }

    fun next() = apply(SurveyWizard.next(_state.value.wizard, today))

    fun skip() = apply(SurveyWizard.skip(_state.value.wizard, today))

    private fun apply(advance: WizardAdvance) {
        when (advance) {
            is WizardAdvance.Moved -> _state.update { it.copy(wizard = advance.state) }
            is WizardAdvance.Finish -> save(advance.draft)
        }
    }

    private fun save(draft: SurveyDraft) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = buildSurveyResult(context, draft, today, createdAt = today)
            budgetRepository.completeOnboarding(result, CategoryPresets.build(context))
            val reminder = ReminderSettings(
                mode = draft.reminderMode ?: ReminderDefaults.suggested(draft.timeCommitment),
            )
            settings.setReminder(reminder)
            scheduler.apply(reminder)
            _events.send(Unit)
        }
    }
}
