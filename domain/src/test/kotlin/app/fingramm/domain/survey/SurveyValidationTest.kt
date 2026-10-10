package app.fingramm.domain.survey

import app.fingramm.domain.model.InputMode
import app.fingramm.domain.model.MainGoalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SurveyValidationTest {
    private val today = LocalDate.of(2026, 10, 10)

    private fun errors(step: SurveyStep, draft: SurveyDraft) = SurveyValidation.validate(step, draft, today)

    private fun codes(step: SurveyStep, draft: SurveyDraft) = errors(step, draft).map { it.field to it.code }

    @Test
    fun `family requires size and children answer`() {
        assertEquals(
            listOf(
                SurveyField.FAMILY_SIZE to SurveyErrorCode.REQUIRED,
                SurveyField.HAS_CHILDREN to SurveyErrorCode.REQUIRED,
            ),
            codes(SurveyStep.FAMILY, SurveyDraft()),
        )
        assertTrue(errors(SurveyStep.FAMILY, SurveyDraft(familySize = 1, hasChildren = false)).isEmpty())
    }

    @Test
    fun `family size must be within range`() {
        val zero = codes(SurveyStep.FAMILY, SurveyDraft(familySize = 0, hasChildren = false))
        assertEquals(listOf(SurveyField.FAMILY_SIZE to SurveyErrorCode.OUT_OF_RANGE), zero)
        val big = codes(SurveyStep.FAMILY, SurveyDraft(familySize = 21, hasChildren = false))
        assertEquals(listOf(SurveyField.FAMILY_SIZE to SurveyErrorCode.OUT_OF_RANGE), big)
    }

    @Test
    fun `children count is bounded by family size minus one adult`() {
        val base = SurveyDraft(familySize = 3, hasChildren = true)
        assertEquals(
            listOf(SurveyField.CHILDREN_COUNT to SurveyErrorCode.REQUIRED),
            codes(SurveyStep.FAMILY, base),
        )
        assertTrue(errors(SurveyStep.FAMILY, base.copy(childrenCount = 2)).isEmpty())
        assertEquals(
            listOf(SurveyField.CHILDREN_COUNT to SurveyErrorCode.OUT_OF_RANGE),
            codes(SurveyStep.FAMILY, base.copy(childrenCount = 3)),
        )
        assertEquals(
            listOf(SurveyField.CHILDREN_COUNT to SurveyErrorCode.OUT_OF_RANGE),
            codes(SurveyStep.FAMILY, base.copy(childrenCount = 0)),
        )
    }

    @Test
    fun `income needs at least one amount but allows zero`() {
        assertEquals(
            listOf(SurveyField.INCOME to SurveyErrorCode.REQUIRED),
            codes(SurveyStep.INCOME, SurveyDraft()),
        )
        assertTrue(errors(SurveyStep.INCOME, SurveyDraft(incomeSources = listOf(DraftAmount("", 0)))).isEmpty())
    }

    @Test
    fun `income row with a name but no amount is flagged`() {
        val draft = SurveyDraft(incomeSources = listOf(DraftAmount("Зарплата", 100), DraftAmount("Аренда", null)))
        assertEquals(
            listOf(SurveyError(SurveyField.INCOME, SurveyErrorCode.REQUIRED, 1)),
            errors(SurveyStep.INCOME, draft),
        )
    }

    @Test
    fun `mandatory expenses by mode`() {
        assertEquals(1, errors(SurveyStep.MANDATORY, SurveyDraft()).size)
        assertTrue(errors(SurveyStep.MANDATORY, SurveyDraft(mandatory = ExpenseDraft(totalMinor = 0))).isEmpty())
        val byCategory = ExpenseDraft(mode = InputMode.BY_CATEGORY, items = listOf(DraftAmount("Аренда", null)))
        assertEquals(1, errors(SurveyStep.MANDATORY, SurveyDraft(mandatory = byCategory)).size)
        val filled = byCategory.copy(items = listOf(DraftAmount("Аренда", 100)))
        assertTrue(errors(SurveyStep.MANDATORY, SurveyDraft(mandatory = filled)).isEmpty())
    }

    @Test
    fun `variable expenses can be unknown but mandatory cannot`() {
        val unknown = ExpenseDraft(unknown = true)
        assertTrue(errors(SurveyStep.VARIABLE, SurveyDraft(variable = unknown)).isEmpty())
        assertEquals(1, errors(SurveyStep.MANDATORY, SurveyDraft(mandatory = unknown)).size)
    }

    @Test
    fun `debts need an answer and a payment when present`() {
        assertEquals(listOf(SurveyField.HAS_DEBTS to SurveyErrorCode.REQUIRED), codes(SurveyStep.DEBTS, SurveyDraft()))
        assertTrue(errors(SurveyStep.DEBTS, SurveyDraft(hasDebts = false)).isEmpty())
        assertEquals(
            listOf(SurveyField.DEBT_PAYMENT to SurveyErrorCode.REQUIRED),
            codes(SurveyStep.DEBTS, SurveyDraft(hasDebts = true)),
        )
        assertTrue(errors(SurveyStep.DEBTS, SurveyDraft(hasDebts = true, debtPaymentMinor = 500)).isEmpty())
    }

    @Test
    fun `cushion needs a positive amount when present`() {
        assertEquals(
            listOf(SurveyField.HAS_CUSHION to SurveyErrorCode.REQUIRED),
            codes(SurveyStep.CUSHION, SurveyDraft()),
        )
        assertTrue(errors(SurveyStep.CUSHION, SurveyDraft(hasCushion = false)).isEmpty())
        assertEquals(
            listOf(SurveyField.CUSHION_AMOUNT to SurveyErrorCode.REQUIRED),
            codes(SurveyStep.CUSHION, SurveyDraft(hasCushion = true)),
        )
        assertEquals(
            listOf(SurveyField.CUSHION_AMOUNT to SurveyErrorCode.MUST_BE_POSITIVE),
            codes(SurveyStep.CUSHION, SurveyDraft(hasCushion = true, cushionMinor = 0)),
        )
    }

    @Test
    fun `assets ignore blank rows but flag a name without value`() {
        val draft = SurveyDraft(assets = listOf(DraftAmount(), DraftAmount("Дача", null), DraftAmount("Авто", 5)))
        assertEquals(
            listOf(SurveyError(SurveyField.ASSET, SurveyErrorCode.REQUIRED, 1)),
            errors(SurveyStep.ASSETS, draft),
        )
    }

    @Test
    fun `main goal is optional and validated when chosen`() {
        assertTrue(errors(SurveyStep.MAIN_GOAL, SurveyDraft()).isEmpty())
        val other = SurveyDraft(mainGoal = MainGoalDraft(kind = MainGoalKind.OTHER))
        assertEquals(
            listOf(SurveyField.MAIN_GOAL_TITLE to SurveyErrorCode.REQUIRED),
            codes(SurveyStep.MAIN_GOAL, other),
        )
        val past = SurveyDraft(mainGoal = MainGoalDraft(MainGoalKind.VACATION, targetMinor = 100, deadline = today))
        assertEquals(
            listOf(SurveyField.MAIN_GOAL_DEADLINE to SurveyErrorCode.DATE_IN_PAST),
            codes(SurveyStep.MAIN_GOAL, past),
        )
        val zero = SurveyDraft(mainGoal = MainGoalDraft(MainGoalKind.CUSHION, targetMinor = 0))
        assertEquals(
            listOf(SurveyField.MAIN_GOAL_TARGET to SurveyErrorCode.MUST_BE_POSITIVE),
            codes(SurveyStep.MAIN_GOAL, zero),
        )
        val ok = SurveyDraft(mainGoal = MainGoalDraft(MainGoalKind.CUSHION, deadline = today.plusDays(1)))
        assertTrue(errors(SurveyStep.MAIN_GOAL, ok).isEmpty())
    }

    @Test
    fun `mid goals need title and target`() {
        val draft = SurveyDraft(
            midGoals = listOf(GoalDraft(), GoalDraft(title = "Квартира"), GoalDraft(targetMinor = 5)),
        )
        assertEquals(
            listOf(
                SurveyError(SurveyField.MID_GOAL_TARGET, SurveyErrorCode.REQUIRED, 1),
                SurveyError(SurveyField.MID_GOAL_TITLE, SurveyErrorCode.REQUIRED, 2),
            ),
            errors(SurveyStep.MID_GOALS, draft),
        )
    }

    @Test
    fun `validateAll reports only failing steps`() {
        val result = SurveyValidation.validateAll(SurveyDraft(), today)
        assertEquals(
            setOf(
                SurveyStep.FAMILY,
                SurveyStep.INCOME,
                SurveyStep.MANDATORY,
                SurveyStep.VARIABLE,
                SurveyStep.DEBTS,
                SurveyStep.CUSHION,
            ),
            result.keys,
        )
    }
}
