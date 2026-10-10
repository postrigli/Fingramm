package app.fingramm.domain.metrics

/**
 * Все ориентирные значения показателей в одном месте. Это общепринятые ориентиры,
 * а не персональная финансовая рекомендация. Тексты подсказок в strings.xml подставляют эти числа.
 */
public object Benchmarks {
    /** Норма сбережений / свободный поток: целевой диапазон, % дохода. */
    public const val SAVINGS_RATE_MIN_PERCENT: Int = 10
    public const val SAVINGS_RATE_MAX_PERCENT: Int = 20

    /** Долговая нагрузка: до этого значения — норма, выше [DEBT_LOAD_RISK_PERCENT] — риск. */
    public const val DEBT_LOAD_NORM_PERCENT: Int = 30
    public const val DEBT_LOAD_RISK_PERCENT: Int = 40

    /** Подушка в месяцах расходов; при нестабильном доходе ориентир выше. */
    public const val CUSHION_MIN_MONTHS: Int = 3
    public const val CUSHION_MAX_MONTHS: Int = 6
    public const val CUSHION_RISK_BELOW_MONTHS: Int = 1

    /** Правило 50/30/20: обязательные расходы до 50% дохода; выше [MANDATORY_SHARE_RISK_PERCENT] — риск. */
    public const val MANDATORY_SHARE_NORM_PERCENT: Int = 50
    public const val MANDATORY_SHARE_RISK_PERCENT: Int = 70

    /** Доля лимита категории, с которой индикатор становится «внимание». */
    public const val LIMIT_ATTENTION_PERCENT: Int = 80
}
