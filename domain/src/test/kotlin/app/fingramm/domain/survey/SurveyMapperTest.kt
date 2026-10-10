package app.fingramm.domain.survey

import app.fingramm.domain.model.BookkeepingExperience
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.GoalHorizon
import app.fingramm.domain.model.InputMode
import app.fingramm.domain.model.MainGoalKind
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.SavingsPlace
import app.fingramm.domain.model.TimeCommitment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SurveyMapperTest {
    private val today = LocalDate.of(2026, 10, 10)
    private val names = DefaultNames(income = "Доход", asset = "Актив", debt = "Кредиты и долги")

    private val draft = SurveyDraft(
        currency = CurrencyCode.USD,
        familySize = 4,
        hasChildren = true,
        childrenCount = 2,
        incomeSources = listOf(DraftAmount("Зарплата", 500_000), DraftAmount("", 100_000), DraftAmount()),
        mandatory = ExpenseDraft(
            mode = InputMode.BY_CATEGORY,
            items = listOf(DraftAmount("Аренда", 200_000), DraftAmount("ЖКХ", null)),
        ),
        variable = ExpenseDraft(unknown = true),
        hasDebts = true,
        debtPaymentMinor = 50_000,
        debtBalanceMinor = null,
        hasCushion = true,
        cushionMinor = 300_000,
        savings = listOf(SavingsDraft(SavingsPlace.DEPOSITS, 1_000), SavingsDraft(SavingsPlace.CASH, null)),
        assets = listOf(DraftAmount("Авто", 800_000), DraftAmount()),
        includeAssetsInCapital = true,
        mainGoal = MainGoalDraft(MainGoalKind.CUSHION, targetMinor = 600_000, deadline = today.plusMonths(6)),
        midGoals = listOf(GoalDraft(), GoalDraft("Квартира", 9_000_000, today.plusYears(3))),
        experience = BookkeepingExperience.EXCEL,
        timeCommitment = TimeCommitment.WEEKLY,
    )

    @Test
    fun `toResult builds all entities and drops blank rows`() {
        val result = SurveyMapper.toResult(draft, today, defaultNames = names) { "Подушка" }

        assertEquals(4, result.profile.familySize)
        assertEquals(2, result.profile.childrenCount)
        assertEquals(CurrencyCode.USD, result.profile.currency)
        assertEquals(today, result.profile.createdAt)

        assertEquals(listOf("Зарплата", "Доход"), result.incomeSources.map { it.name })
        assertEquals(listOf(500_000L, 100_000L), result.incomeSources.map { it.amountMinor })
        assertEquals(1, result.assets.size)

        assertEquals(1, result.debts.size)
        assertEquals(50_000, result.debts.single().monthlyPaymentMinor)
        assertNull(result.debts.single().balanceMinor)

        assertEquals(200_000, result.survey.mandatoryMonthlyMinor)
        assertEquals(listOf(200_000L), result.survey.mandatoryItems.map { it.amountMinor })
        assertTrue(result.survey.variableUnknown)
        assertEquals(300_000, result.survey.cushionMinor)
        assertEquals(listOf(1_000L, 0L), result.survey.savings.map { it.amountMinor })
    }

    @Test
    fun `main goal and mid goals become goals with horizons`() {
        val goals = SurveyMapper.toResult(draft, today, defaultNames = names) { "Подушка" }.goals
        assertEquals(listOf("Подушка", "Квартира"), goals.map { it.title })
        assertEquals(listOf(GoalHorizon.YEAR, GoalHorizon.MID), goals.map { it.horizon })
        // Существующая подушка засчитывается в прогресс главной цели «подушка».
        assertEquals(300_000, goals.first().savedMinor)
        assertEquals(0, goals.last().savedMinor)
    }

    @Test
    fun `custom main goal uses typed title`() {
        val custom = draft.copy(mainGoal = MainGoalDraft(MainGoalKind.OTHER, customTitle = " Ремонт ", targetMinor = 5))
        val goals = SurveyMapper.toResult(custom, today, defaultNames = names) { "x" }.goals
        assertEquals("Ремонт", goals.first().title)
        assertEquals(0, goals.first().savedMinor)
    }

    @Test
    fun `no debts and no cushion produce empty entities`() {
        val d = draft.copy(hasDebts = false, hasCushion = false, cushionMinor = 5, hasChildren = false)
        val result = SurveyMapper.toResult(d, today, defaultNames = names) { "x" }
        assertTrue(result.debts.isEmpty())
        assertEquals(0, result.survey.cushionMinor)
        assertEquals(0, result.profile.childrenCount)
    }

    @Test
    fun `toDraft restores the answers`() {
        val result = SurveyMapper.toResult(draft, today, defaultNames = names) { "x" }
        val restored = restore(result)
        assertEquals(draft.currency, restored.currency)
        assertEquals(4, restored.familySize)
        assertEquals(2, restored.childrenCount)
        assertEquals(true, restored.hasDebts)
        assertEquals(50_000L, restored.debtPaymentMinor)
        assertEquals(300_000L, restored.cushionMinor)
        assertEquals(true, restored.variable.unknown)
        assertEquals(InputMode.BY_CATEGORY, restored.mandatory.mode)
        assertEquals(BookkeepingExperience.EXCEL, restored.experience)
        assertTrue(SurveyValidation.validateAll(restored, today).isEmpty())
    }

    @Test
    fun `toDraft always keeps one income row to edit`() {
        val result = SurveyMapper.toResult(draft.copy(incomeSources = emptyList()), today, defaultNames = names) { "x" }
        val restored = restore(result)
        assertEquals(1, restored.incomeSources.size)
    }

    private fun restore(result: SurveyResult) = SurveyMapper.toDraft(
        result.profile,
        result.survey,
        result.incomeSources,
        result.assets,
        result.debts,
    )
}

class ReminderDefaultsTest {
    @Test
    fun `suggested mode follows time commitment`() {
        assertEquals(ReminderMode.DAILY_EVENING, ReminderDefaults.suggested(TimeCommitment.DAILY))
        assertEquals(ReminderMode.DAILY_EVENING, ReminderDefaults.suggested(TimeCommitment.FEW_MINUTES_DAILY))
        assertEquals(ReminderMode.WEEKLY, ReminderDefaults.suggested(TimeCommitment.WEEKLY))
        assertEquals(ReminderMode.WEEKLY, ReminderDefaults.suggested(TimeCommitment.MONTHLY))
        assertEquals(ReminderMode.OFF, ReminderDefaults.suggested(null))
    }
}
