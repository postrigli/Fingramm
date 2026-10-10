package app.fingramm.domain.metrics

import app.fingramm.domain.model.Asset
import app.fingramm.domain.model.Debt
import app.fingramm.domain.model.IncomeSource
import app.fingramm.domain.model.InputMode
import app.fingramm.domain.model.NamedAmount
import app.fingramm.domain.model.SavingsEntry
import app.fingramm.domain.model.SavingsPlace
import app.fingramm.domain.model.SurveyAnswers
import app.fingramm.domain.stats.MonthTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class SnapshotsTest {
    private val baseSurvey = SurveyAnswers(
        mandatoryTotalMinor = 40_000,
        variableTotalMinor = 20_000,
        hasCushion = true,
        cushionMinor = 90_000,
        savings = listOf(SavingsEntry(SavingsPlace.DEPOSITS, 50_000)),
    )

    private fun inputs(
        survey: SurveyAnswers = baseSurvey,
        debts: List<Debt> = emptyList(),
        assets: List<Asset> = emptyList(),
    ) = PlanInputs(
        survey = survey,
        incomeSources = listOf(IncomeSource(1, "Зарплата", 70_000), IncomeSource(2, "Подработка", 30_000)),
        debts = debts,
        assets = assets,
    )

    @Test
    fun `plan sums income and uses declared expenses`() {
        val plan = Snapshots.plan(inputs())
        assertEquals(100_000, plan.cashFlow.incomeMinor)
        assertEquals(40_000, plan.cashFlow.mandatoryMinor)
        assertEquals(20_000, plan.cashFlow.variableMinor)
        assertEquals(VariableSource.DECLARED, plan.variableSource)
    }

    @Test
    fun `by-category mode sums items`() {
        val survey = baseSurvey.copy(
            mandatoryMode = InputMode.BY_CATEGORY,
            mandatoryItems = listOf(NamedAmount("Аренда", 25_000), NamedAmount("ЖКХ", 5_000)),
            mandatoryTotalMinor = 999,
        )
        assertEquals(30_000, Snapshots.plan(inputs(survey)).cashFlow.mandatoryMinor)
    }

    @Test
    fun `unknown variable expenses fall back to estimate then to zero`() {
        val survey = baseSurvey.copy(variableUnknown = true)
        val unknown = Snapshots.plan(inputs(survey))
        assertEquals(0, unknown.cashFlow.variableMinor)
        assertEquals(VariableSource.UNKNOWN, unknown.variableSource)

        val estimated = Snapshots.plan(inputs(survey), estimatedVariableMinor = 18_000)
        assertEquals(18_000, estimated.cashFlow.variableMinor)
        assertEquals(VariableSource.ESTIMATED_FROM_FACTS, estimated.variableSource)
    }

    @Test
    fun `cushion is part of savings and is not counted twice`() {
        // Сумма по местам хранения (50 000) меньше подушки (90 000) → берём подушку.
        assertEquals(90_000, Snapshots.position(inputs()).liquidSavingsMinor)
        val more = baseSurvey.copy(savings = listOf(SavingsEntry(SavingsPlace.CASH, 200_000)))
        assertEquals(200_000, Snapshots.position(inputs(more)).liquidSavingsMinor)
    }

    @Test
    fun `assets are included only when switch is on`() {
        val assets = listOf(Asset(1, "Авто", 500_000))
        assertEquals(0, Snapshots.position(inputs(assets = assets)).assetsMinor)
        val on = baseSurvey.copy(includeAssetsInCapital = true)
        assertEquals(500_000, Snapshots.position(inputs(on, assets = assets)).assetsMinor)
    }

    @Test
    fun `missing debt balance is flagged`() {
        val debts = listOf(Debt(1, "Кредит", monthlyPaymentMinor = 10_000, balanceMinor = null))
        val plan = Snapshots.plan(inputs(debts = debts))
        assertTrue(plan.debtBalanceMissing)
        assertEquals(0, plan.position.debtBalanceMinor)
        assertEquals(10_000, plan.cashFlow.debtPaymentMinor)

        val known = Snapshots.plan(inputs(debts = listOf(debts[0].copy(balanceMinor = 300_000))))
        assertFalse(known.debtBalanceMissing)
        assertEquals(300_000, known.position.debtBalanceMinor)
    }

    @Test
    fun `fact moves liquid savings by the cumulative balance`() {
        val month = MonthTotals(
            YearMonth.of(2026, 10),
            incomeMinor = 120_000,
            mandatoryMinor = 50_000,
            variableMinor = 30_000,
        )
        val (flow, position) = Snapshots.fact(month, Position(0, 90_000, 0, 0), cumulativeBalanceMinor = 40_000)
        assertEquals(120_000, flow.incomeMinor)
        assertEquals(130_000, position.liquidSavingsMinor)
    }
}
