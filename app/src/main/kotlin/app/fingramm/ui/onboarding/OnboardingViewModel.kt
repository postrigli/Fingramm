package app.fingramm.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.domain.model.ReminderSettings
import app.fingramm.domain.survey.MainGoalDraft
import app.fingramm.domain.survey.ReminderDefaults
import app.fingramm.domain.survey.SurveyDraft
import app.fingramm.domain.survey.SurveyStep
import app.fingramm.domain.survey.SurveyValidation
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

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgetRepository: BudgetRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(SurveyFormState())
    val state: StateFlow<SurveyFormState> = _state.asStateFlow()

    private val _events = Channel<Unit>(Channel.BUFFERED)

    /** Срабатывает один раз, когда опрос сохранён и можно открывать дашборд. */
    val finished = _events.receiveAsFlow()

    val today: LocalDate get() = LocalDate.now(clock)

    private val steps = SurveyStep.entries

    fun update(transform: (SurveyDraft) -> SurveyDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    fun back() {
        _state.update {
            val index = steps.indexOf(it.step)
            if (index > 0) it.copy(step = steps[index - 1], showErrors = false) else it
        }
    }

    /** Переход вперёд с проверкой текущего шага; на последнем шаге сохраняет опрос. */
    fun next() {
        val current = _state.value
        if (current.errorsFor(current.step, today).isNotEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val index = steps.indexOf(current.step)
        if (index == steps.lastIndex) {
            finish()
        } else {
            _state.update { it.copy(step = steps[index + 1], showErrors = false) }
        }
    }

    /** Пропуск необязательного шага: введённое на нём сбрасывается, чтобы не оставить неполных данных. */
    fun skip() {
        val current = _state.value
        if (!current.step.optional) return
        update {
            when (current.step) {
                SurveyStep.ASSETS -> it.copy(assets = emptyList(), includeAssetsInCapital = false)
                SurveyStep.MAIN_GOAL -> it.copy(mainGoal = MainGoalDraft())
                SurveyStep.MID_GOALS -> it.copy(midGoals = emptyList())
                SurveyStep.HABITS -> it.copy(experience = null, timeCommitment = null, reminderMode = null)
                else -> it
            }
        }
        val index = steps.indexOf(current.step)
        if (index == steps.lastIndex) {
            finish()
        } else {
            _state.update {
                it.copy(
                    step = steps[index + 1],
                    showErrors = false,
                )
            }
        }
    }

    private fun finish() {
        val draft = _state.value.draft
        val failing = SurveyValidation.validateAll(draft, today)
        if (failing.isNotEmpty()) {
            _state.update { it.copy(step = failing.keys.first(), showErrors = true) }
            return
        }
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
