package app.fingramm.domain.survey

import java.time.LocalDate

/** Состояние мастера опроса: введённые ответы, текущий шаг и показывать ли ошибки. */
public data class WizardState(
    val draft: SurveyDraft = SurveyDraft(),
    val step: SurveyStep = SurveyStep.WELCOME,
    val showErrors: Boolean = false,
) {
    public val stepIndex: Int get() = SurveyStep.entries.indexOf(step)

    /** Число вопросов (без приветствия). */
    public val questionCount: Int get() = SurveyStep.entries.lastIndex

    public val isLastStep: Boolean get() = step == SurveyStep.entries.last()
}

/** Результат попытки перейти дальше. */
public sealed interface WizardAdvance {
    public data class Moved(val state: WizardState) : WizardAdvance

    /** Все шаги пройдены и валидны — можно сохранять. */
    public data class Finish(val draft: SurveyDraft) : WizardAdvance
}

/** Переходы мастера как чистые функции: один код для экрана, ViewModel и тестов. */
public object SurveyWizard {
    private val steps = SurveyStep.entries

    /** Ошибки текущего шага, видимые пользователю (пусто, пока он не пытался пойти дальше). */
    public fun visibleErrors(state: WizardState, today: LocalDate): List<SurveyError> =
        if (state.showErrors) SurveyValidation.validate(state.step, state.draft, today) else emptyList()

    public fun back(state: WizardState): WizardState {
        val index = state.stepIndex
        return if (index > 0) state.copy(step = steps[index - 1], showErrors = false) else state
    }

    /** «Далее»: проверяет шаг; на последнем шаге проверяет весь опрос. */
    public fun next(state: WizardState, today: LocalDate): WizardAdvance {
        if (SurveyValidation.validate(state.step, state.draft, today).isNotEmpty()) {
            return WizardAdvance.Moved(state.copy(showErrors = true))
        }
        return advance(state, today)
    }

    /** «Пропустить» необязательный шаг: введённое на нём сбрасывается, чтобы не остаться с неполными данными. */
    public fun skip(state: WizardState, today: LocalDate): WizardAdvance {
        if (!state.step.optional) return WizardAdvance.Moved(state)
        val cleared = when (state.step) {
            SurveyStep.ASSETS -> state.draft.copy(assets = emptyList(), includeAssetsInCapital = false)
            SurveyStep.MAIN_GOAL -> state.draft.copy(mainGoal = MainGoalDraft())
            SurveyStep.MID_GOALS -> state.draft.copy(midGoals = emptyList())
            SurveyStep.HABITS -> state.draft.copy(experience = null, timeCommitment = null, reminderMode = null)
            else -> state.draft
        }
        return advance(state.copy(draft = cleared), today)
    }

    private fun advance(state: WizardState, today: LocalDate): WizardAdvance {
        if (!state.isLastStep) {
            return WizardAdvance.Moved(state.copy(step = steps[state.stepIndex + 1], showErrors = false))
        }
        val failing = SurveyValidation.validateAll(state.draft, today)
        return if (failing.isEmpty()) {
            WizardAdvance.Finish(state.draft)
        } else {
            WizardAdvance.Moved(state.copy(step = failing.keys.first(), showErrors = true))
        }
    }
}
