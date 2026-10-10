package app.fingramm.domain.metrics

import app.fingramm.domain.model.Asset
import app.fingramm.domain.model.Debt
import app.fingramm.domain.model.IncomeSource
import app.fingramm.domain.model.SurveyAnswers
import app.fingramm.domain.stats.MonthTotals

/** Откуда взяты переменные расходы в плане. */
public enum class VariableSource { DECLARED, ESTIMATED_FROM_FACTS, UNKNOWN }

/** Плановые данные из опроса, нужные для расчётов. */
public data class PlanInputs(
    val survey: SurveyAnswers,
    val incomeSources: List<IncomeSource>,
    val debts: List<Debt>,
    val assets: List<Asset>,
)

public data class PlanSnapshot(
    val cashFlow: CashFlow,
    val position: Position,
    val variableSource: VariableSource,
    /** Остаток долга не указан, хотя платежи есть — чистый капитал может быть занижен/завышен. */
    val debtBalanceMissing: Boolean,
)

/** Сборка расчётных срезов «план» (из опроса) и «факт» (из операций месяца). */
public object Snapshots {
    /**
     * План из ответов опроса. [estimatedVariableMinor] — оценка по фактам; используется,
     * только если пользователь ответил «не знаю».
     */
    public fun plan(inputs: PlanInputs, estimatedVariableMinor: Long? = null): PlanSnapshot {
        val survey = inputs.survey
        val variable: Long
        val source: VariableSource
        when {
            !survey.variableUnknown -> {
                variable = survey.variableDeclaredMinor
                source = VariableSource.DECLARED
            }
            estimatedVariableMinor != null -> {
                variable = estimatedVariableMinor
                source = VariableSource.ESTIMATED_FROM_FACTS
            }
            else -> {
                variable = 0
                source = VariableSource.UNKNOWN
            }
        }
        val cashFlow = CashFlow(
            incomeMinor = inputs.incomeSources.sumOf { it.amountMinor },
            mandatoryMinor = survey.mandatoryMonthlyMinor,
            variableMinor = variable,
            debtPaymentMinor = inputs.debts.sumOf { it.monthlyPaymentMinor },
        )
        return PlanSnapshot(
            cashFlow = cashFlow,
            position = position(inputs),
            variableSource = source,
            debtBalanceMissing = inputs.debts.any { it.monthlyPaymentMinor > 0 && it.balanceMinor == null },
        )
    }

    /**
     * Позиция по опросу. Подушка — часть сбережений: ликвидные сбережения =
     * max(сумма по местам хранения, подушка), чтобы не считать подушку дважды.
     */
    public fun position(inputs: PlanInputs): Position {
        val survey = inputs.survey
        val cushion = if (survey.hasCushion) survey.cushionMinor else 0L
        return Position(
            cushionMinor = cushion,
            liquidSavingsMinor = maxOf(survey.savingsTotalMinor, cushion),
            assetsMinor = if (survey.includeAssetsInCapital) inputs.assets.sumOf { it.valueMinor } else 0L,
            debtBalanceMinor = inputs.debts.sumOf { it.balanceMinor ?: 0L },
        )
    }

    /**
     * Факт за месяц: поток из операций, позиция — исходная плюс накопленный результат операций
     * (доходы − расходы) к концу этого месяца.
     */
    public fun fact(
        month: MonthTotals,
        basePosition: Position,
        cumulativeBalanceMinor: Long,
    ): Pair<CashFlow, Position> {
        val cashFlow = CashFlow(
            incomeMinor = month.incomeMinor,
            mandatoryMinor = month.mandatoryMinor,
            variableMinor = month.variableMinor,
            debtPaymentMinor = month.debtPaymentMinor,
        )
        val position = basePosition.copy(
            liquidSavingsMinor = basePosition.liquidSavingsMinor + cumulativeBalanceMinor,
        )
        return cashFlow to position
    }
}
