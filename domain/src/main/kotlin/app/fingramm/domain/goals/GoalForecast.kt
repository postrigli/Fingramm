package app.fingramm.domain.goals

import app.fingramm.domain.model.Goal
import app.fingramm.domain.money.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit

public data class GoalForecast(
    /** Сколько ещё нужно накопить; 0, если цель достигнута или сумма не задана. */
    val remainingMinor: Long,
    /** Прогресс 0..1 (не больше 1); null, если целевая сумма не задана. */
    val progress: Double?,
    val reached: Boolean,
    /** Через сколько месяцев цель будет достигнута при текущем свободном потоке; null — недостижима/нет данных. */
    val monthsAtCurrentFlow: Int?,
    val projectedDate: LocalDate?,
    /** Сколько нужно откладывать в месяц, чтобы успеть к сроку. */
    val requiredPerMonthMinor: Long?,
    val deadlinePassed: Boolean,
    /** Хватает ли текущего потока, чтобы успеть к сроку; null, если срока или суммы нет. */
    val onTrack: Boolean?,
)

public object GoalForecasts {
    private const val DAYS_PER_MONTH = 30.4375

    /**
     * Прогноз по цели. [monthlyFlowMinor] — свободный денежный поток в месяц; считаем, что он целиком
     * направляется на эту цель (упрощение, зафиксировано в DECISIONS.md).
     */
    public fun forecast(goal: Goal, monthlyFlowMinor: Long, today: LocalDate): GoalForecast {
        val target = goal.targetMinor
        if (target == null || target <= 0) {
            return GoalForecast(0, null, false, null, null, null, deadlinePassed = false, onTrack = null)
        }
        val remaining = (target - goal.savedMinor).coerceAtLeast(0)
        val progress = (Money.ratio(goal.savedMinor, target) ?: 0.0).coerceIn(0.0, 1.0)
        if (remaining == 0L) {
            return GoalForecast(0, 1.0, true, 0, today, 0, deadlinePassed = false, onTrack = true)
        }
        val months = if (monthlyFlowMinor > 0) Money.ceilDiv(remaining, monthlyFlowMinor) else null
        val monthsInt = months?.coerceAtMost(MAX_MONTHS)?.toInt()
        val deadline = goal.deadline
        val passed = deadline != null && !deadline.isAfter(today)
        val required = if (deadline != null && !passed) Money.ceilDiv(remaining, monthsUntil(today, deadline)) else null
        val onTrack = required?.let { monthlyFlowMinor >= it }
        return GoalForecast(
            remainingMinor = remaining,
            progress = progress,
            reached = false,
            monthsAtCurrentFlow = monthsInt,
            projectedDate = monthsInt?.let { today.plusMonths(it.toLong()) },
            requiredPerMonthMinor = required,
            deadlinePassed = passed,
            onTrack = onTrack,
        )
    }

    /** Сколько месяцев осталось до срока, округляя вверх; минимум 1. */
    public fun monthsUntil(today: LocalDate, deadline: LocalDate): Long {
        val days = ChronoUnit.DAYS.between(today, deadline).coerceAtLeast(1)
        return Math.ceil(days / DAYS_PER_MONTH).toLong().coerceAtLeast(1)
    }

    private const val MAX_MONTHS = 1200L
}
