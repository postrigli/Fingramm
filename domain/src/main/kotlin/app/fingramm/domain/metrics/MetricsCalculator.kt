package app.fingramm.domain.metrics

import app.fingramm.domain.money.Money

/** Расчёт показателей дашборда. Чистые функции, без побочных эффектов. */
public object MetricsCalculator {
    public fun calculate(cashFlow: CashFlow, position: Position): MetricsReport = MetricsReport(
        cashFlow = cashFlow,
        position = position,
        results = listOf(
            freeCashFlow(cashFlow),
            savingsRate(cashFlow),
            debtLoad(cashFlow),
            cushionMonths(cashFlow, position),
            mandatoryShare(cashFlow),
            netWorth(position),
        ),
    )

    /** Доход − обязательные − переменные − платежи по долгам. Цель: > 0, в идеале 10–20% дохода. */
    internal fun freeCashFlow(cf: CashFlow): MetricResult {
        val value = cf.freeCashFlowMinor
        val status = when {
            value < 0 -> MetricStatus.RISK
            cf.incomeMinor == 0L && cf.outflowMinor == 0L -> MetricStatus.NEUTRAL
            cf.incomeMinor > 0 &&
                Money.compareToPercent(value, cf.incomeMinor, Benchmarks.SAVINGS_RATE_MIN_PERCENT) < 0 ->
                MetricStatus.ATTENTION
            else -> MetricStatus.GOOD
        }
        return MetricResult(MetricId.FREE_CASH_FLOW, status, moneyMinor = value)
    }

    /** (доход − все расходы) / доход. При нулевом доходе не определена. */
    internal fun savingsRate(cf: CashFlow): MetricResult {
        if (cf.incomeMinor <= 0) {
            val status = if (cf.outflowMinor > 0) MetricStatus.RISK else MetricStatus.NEUTRAL
            return MetricResult(MetricId.SAVINGS_RATE, status)
        }
        val value = cf.freeCashFlowMinor
        val status = when {
            value < 0 -> MetricStatus.RISK
            Money.compareToPercent(value, cf.incomeMinor, Benchmarks.SAVINGS_RATE_MIN_PERCENT) < 0 ->
                MetricStatus.ATTENTION
            else -> MetricStatus.GOOD
        }
        return MetricResult(MetricId.SAVINGS_RATE, status, ratio = Money.ratio(value, cf.incomeMinor))
    }

    /** Платежи по долгам / доход. Без платежей — 0% и норма, даже если дохода нет. */
    internal fun debtLoad(cf: CashFlow): MetricResult {
        if (cf.debtPaymentMinor <= 0) {
            return MetricResult(MetricId.DEBT_LOAD, MetricStatus.GOOD, ratio = 0.0)
        }
        if (cf.incomeMinor <= 0) return MetricResult(MetricId.DEBT_LOAD, MetricStatus.RISK)
        val status = when {
            Money.compareToPercent(cf.debtPaymentMinor, cf.incomeMinor, Benchmarks.DEBT_LOAD_RISK_PERCENT) > 0 ->
                MetricStatus.RISK
            Money.compareToPercent(cf.debtPaymentMinor, cf.incomeMinor, Benchmarks.DEBT_LOAD_NORM_PERCENT) > 0 ->
                MetricStatus.ATTENTION
            else -> MetricStatus.GOOD
        }
        return MetricResult(MetricId.DEBT_LOAD, status, ratio = Money.ratio(cf.debtPaymentMinor, cf.incomeMinor))
    }

    /** Подушка / месячные расходы (обязательные + переменные + платежи). Без расходов не определена. */
    internal fun cushionMonths(cf: CashFlow, position: Position): MetricResult {
        if (cf.outflowMinor <= 0) return MetricResult(MetricId.CUSHION_MONTHS, MetricStatus.NEUTRAL)
        val months = Money.ratio(position.cushionMinor, cf.outflowMinor) ?: 0.0
        val status = when {
            months < Benchmarks.CUSHION_RISK_BELOW_MONTHS -> MetricStatus.RISK
            months < Benchmarks.CUSHION_MIN_MONTHS -> MetricStatus.ATTENTION
            else -> MetricStatus.GOOD
        }
        return MetricResult(MetricId.CUSHION_MONTHS, status, months = months)
    }

    /** Обязательные расходы / доход. Ориентир ≤ 50%. */
    internal fun mandatoryShare(cf: CashFlow): MetricResult {
        if (cf.incomeMinor <= 0) {
            val status = if (cf.mandatoryMinor > 0) MetricStatus.RISK else MetricStatus.NEUTRAL
            return MetricResult(MetricId.MANDATORY_SHARE, status)
        }
        val status = when {
            Money.compareToPercent(cf.mandatoryMinor, cf.incomeMinor, Benchmarks.MANDATORY_SHARE_RISK_PERCENT) > 0 ->
                MetricStatus.RISK
            Money.compareToPercent(cf.mandatoryMinor, cf.incomeMinor, Benchmarks.MANDATORY_SHARE_NORM_PERCENT) > 0 ->
                MetricStatus.ATTENTION
            else -> MetricStatus.GOOD
        }
        return MetricResult(
            MetricId.MANDATORY_SHARE,
            status,
            ratio = Money.ratio(cf.mandatoryMinor, cf.incomeMinor),
        )
    }

    /** Ликвидные сбережения + (опц.) активы − долги. Нормы нет: важна динамика; отрицательный — «внимание». */
    internal fun netWorth(position: Position): MetricResult {
        val value = position.netWorthMinor
        val status = if (value < 0) MetricStatus.ATTENTION else MetricStatus.NEUTRAL
        return MetricResult(MetricId.NET_WORTH, status, moneyMinor = value)
    }
}
