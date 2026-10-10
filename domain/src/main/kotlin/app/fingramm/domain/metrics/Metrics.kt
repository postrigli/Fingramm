package app.fingramm.domain.metrics

public enum class MetricId {
    FREE_CASH_FLOW,
    SAVINGS_RATE,
    DEBT_LOAD,
    CUSHION_MONTHS,
    MANDATORY_SHARE,
    NET_WORTH,
}

/** NEUTRAL — нет данных или у показателя нет нормы. Статус всегда сопровождается текстовой меткой в UI. */
public enum class MetricStatus { GOOD, ATTENTION, RISK, NEUTRAL }

/**
 * Результат расчёта одного показателя. Заполнено не более одного из полей значения;
 * если все пусты — показатель не определён (например, доля при нулевом доходе).
 */
public data class MetricResult(
    val id: MetricId,
    val status: MetricStatus,
    val moneyMinor: Long? = null,
    /** Доля, 0.25 = 25%. */
    val ratio: Double? = null,
    val months: Double? = null,
) {
    val isDefined: Boolean get() = moneyMinor != null || ratio != null || months != null
}

/** Денежный поток за месяц (план или факт). */
public data class CashFlow(
    val incomeMinor: Long,
    val mandatoryMinor: Long,
    val variableMinor: Long,
    val debtPaymentMinor: Long,
) {
    val outflowMinor: Long get() = mandatoryMinor + variableMinor + debtPaymentMinor
    val freeCashFlowMinor: Long get() = incomeMinor - outflowMinor
}

/** Накопления и обязательства на момент расчёта. */
public data class Position(
    val cushionMinor: Long,
    val liquidSavingsMinor: Long,
    /** Уже учитывает переключатель «включать активы в капитал». */
    val assetsMinor: Long,
    val debtBalanceMinor: Long,
) {
    val netWorthMinor: Long get() = liquidSavingsMinor + assetsMinor - debtBalanceMinor
}

public data class MetricsReport(val cashFlow: CashFlow, val position: Position, val results: List<MetricResult>) {
    public operator fun get(id: MetricId): MetricResult = results.first { it.id == id }
}
