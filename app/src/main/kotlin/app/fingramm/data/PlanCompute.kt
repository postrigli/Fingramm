package app.fingramm.data

import app.fingramm.data.repo.BudgetData
import app.fingramm.domain.metrics.PlanSnapshot
import app.fingramm.domain.metrics.Snapshots
import app.fingramm.domain.period.BudgetPeriod
import app.fingramm.domain.stats.MonthTotals
import app.fingramm.domain.stats.StatsCalculator
import java.time.YearMonth

/**
 * План из опроса. Если пользователь ответил «не знаю» про переменные расходы, а полный расчётный месяц
 * операций уже прошёл, план пересчитывается по фактическим операциям.
 *
 * @param current текущий расчётный месяц (см. [BudgetPeriod]); @param startDay день начала месяца из настроек:
 * по нему определяется период, в котором создан профиль (он неполный и в оценку не входит).
 */
fun computePlan(
    budget: BudgetData,
    totals: Map<YearMonth, MonthTotals>,
    current: YearMonth,
    startDay: Int,
): PlanSnapshot {
    val createdPeriod = BudgetPeriod.containing(budget.profile.createdAt, startDay)
    val estimated = StatsCalculator.estimateVariableFromFacts(totals, createdPeriod, current)
    return Snapshots.plan(budget.planInputs, estimated)
}
