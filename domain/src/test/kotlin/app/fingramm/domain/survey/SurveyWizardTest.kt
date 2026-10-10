package app.fingramm.domain.survey

import app.fingramm.domain.model.MainGoalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SurveyWizardTest {
    private val today = LocalDate.of(2026, 10, 10)

    private val valid = SurveyDraft(
        familySize = 2,
        hasChildren = false,
        incomeSources = listOf(DraftAmount("", 100_000)),
        mandatory = ExpenseDraft(totalMinor = 40_000),
        variable = ExpenseDraft(totalMinor = 20_000),
        hasDebts = false,
        hasCushion = false,
    )

    private fun moved(advance: WizardAdvance) = (advance as WizardAdvance.Moved).state

    @Test
    fun `welcome has no validation and moves to family`() {
        val state = moved(SurveyWizard.next(WizardState(), today))
        assertEquals(SurveyStep.FAMILY, state.step)
        assertFalse(state.showErrors)
    }

    @Test
    fun `invalid step stays and shows errors`() {
        val start = WizardState(step = SurveyStep.FAMILY)
        val state = moved(SurveyWizard.next(start, today))
        assertEquals(SurveyStep.FAMILY, state.step)
        assertTrue(state.showErrors)
        assertTrue(SurveyWizard.visibleErrors(state, today).isNotEmpty())
    }

    @Test
    fun `errors are hidden until the user tries to continue`() {
        assertTrue(SurveyWizard.visibleErrors(WizardState(step = SurveyStep.FAMILY), today).isEmpty())
    }

    @Test
    fun `valid step advances and hides errors again`() {
        val start = WizardState(valid, SurveyStep.FAMILY, showErrors = true)
        val state = moved(SurveyWizard.next(start, today))
        assertEquals(SurveyStep.INCOME, state.step)
        assertFalse(state.showErrors)
    }

    @Test
    fun `back returns to the previous step and never goes before welcome`() {
        assertEquals(SurveyStep.FAMILY, SurveyWizard.back(WizardState(valid, SurveyStep.INCOME)).step)
        assertEquals(SurveyStep.WELCOME, SurveyWizard.back(WizardState()).step)
    }

    @Test
    fun `full pass with skipped optional steps finishes`() {
        var state = WizardState(valid)
        var finished: SurveyDraft? = null
        repeat(SurveyStep.entries.size) {
            if (finished != null) return@repeat
            val advance = if (state.step.optional) SurveyWizard.skip(state, today) else SurveyWizard.next(state, today)
            when (advance) {
                is WizardAdvance.Moved -> state = advance.state
                is WizardAdvance.Finish -> finished = advance.draft
            }
        }
        // Обязательные поля CUSHION остаются невалидными (hasCushion=false допустим) — значит, опрос завершён.
        assertEquals(valid.copy(experience = null, timeCommitment = null, reminderMode = null), finished)
    }

    @Test
    fun `skip clears the optional section`() {
        val draft = valid.copy(mainGoal = MainGoalDraft(MainGoalKind.OTHER, customTitle = ""))
        val state = moved(SurveyWizard.skip(WizardState(draft, SurveyStep.MAIN_GOAL), today))
        assertEquals(MainGoalDraft(), state.draft.mainGoal)
        assertEquals(SurveyStep.MID_GOALS, state.step)
    }

    @Test
    fun `skip does nothing on a required step`() {
        val start = WizardState(valid, SurveyStep.INCOME)
        assertEquals(start, moved(SurveyWizard.skip(start, today)))
    }

    @Test
    fun `finishing with a broken earlier step jumps back to it`() {
        val broken = valid.copy(familySize = null)
        val state = moved(SurveyWizard.next(WizardState(broken, SurveyStep.HABITS), today))
        assertEquals(SurveyStep.FAMILY, state.step)
        assertTrue(state.showErrors)
    }

    @Test
    fun `progress helpers`() {
        val state = WizardState(step = SurveyStep.INCOME)
        assertEquals(2, state.stepIndex)
        assertEquals(10, state.questionCount)
        assertTrue(WizardState(step = SurveyStep.HABITS).isLastStep)
    }
}
