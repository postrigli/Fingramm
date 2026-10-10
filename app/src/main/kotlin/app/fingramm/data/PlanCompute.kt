package app.fingramm.data

import app.fingramm.data.repo.BudgetData
import app.fingramm.domain.metrics.PlanSnapshot
import app.fingramm.domain.metrics.Snapshots
import app.fingramm.domain.stats.MonthTotals
import app.fingramm.domain.stats.StatsCalculator
import java.time.YearMonth

/**
 * План из опроса. Если пользователь ответил «не знаю» про переменные расходы, а полный месяц
 * операций уже прошёл, план пересчитывается по фактическим операциям.
 */
fun computePlan(budget: BudgetData, totals: Map<YearMonth, MonthTotals>, current: YearMonth): PlanSnapshot {
    val estimated = StatsCalculator.estimateVariableFromFacts(totals, YearMonth.from(budget.profile.createdAt), current)
    return Snapshots.plan(budget.planInputs, estimated)
}
