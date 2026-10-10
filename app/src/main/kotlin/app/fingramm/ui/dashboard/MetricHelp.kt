package app.fingramm.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fingramm.R
import app.fingramm.domain.metrics.Benchmarks
import app.fingramm.domain.metrics.MetricId
import app.fingramm.domain.metrics.MetricStatus

/** Ресурсы подсказки показателя: что это, как считается, норма и советы. */
data class MetricHelp(
    @StringRes val title: Int,
    @StringRes val what: Int,
    @StringRes val how: Int,
    val tips: List<Int>,
)

fun metricHelp(id: MetricId): MetricHelp = when (id) {
    MetricId.FREE_CASH_FLOW -> MetricHelp(
        R.string.metric_fcf_title,
        R.string.metric_fcf_what,
        R.string.metric_fcf_how,
        listOf(R.string.metric_fcf_tip1, R.string.metric_fcf_tip2, R.string.metric_fcf_tip3),
    )
    MetricId.SAVINGS_RATE -> MetricHelp(
        R.string.metric_savings_title,
        R.string.metric_savings_what,
        R.string.metric_savings_how,
        listOf(R.string.metric_savings_tip1, R.string.metric_savings_tip2, R.string.metric_savings_tip3),
    )
    MetricId.DEBT_LOAD -> MetricHelp(
        R.string.metric_debt_title,
        R.string.metric_debt_what,
        R.string.metric_debt_how,
        listOf(R.string.metric_debt_tip1, R.string.metric_debt_tip2, R.string.metric_debt_tip3),
    )
    MetricId.CUSHION_MONTHS -> MetricHelp(
        R.string.metric_cushion_title,
        R.string.metric_cushion_what,
        R.string.metric_cushion_how,
        listOf(R.string.metric_cushion_tip1, R.string.metric_cushion_tip2, R.string.metric_cushion_tip3),
    )
    MetricId.MANDATORY_SHARE -> MetricHelp(
        R.string.metric_mandatory_title,
        R.string.metric_mandatory_what,
        R.string.metric_mandatory_how,
        listOf(R.string.metric_mandatory_tip1, R.string.metric_mandatory_tip2, R.string.metric_mandatory_tip3),
    )
    MetricId.NET_WORTH -> MetricHelp(
        R.string.metric_net_worth_title,
        R.string.metric_net_worth_what,
        R.string.metric_net_worth_how,
        listOf(R.string.metric_net_worth_tip1, R.string.metric_net_worth_tip2, R.string.metric_net_worth_tip3),
    )
}

/** Полный текст нормы (для подсказки). Числа берутся из [Benchmarks], а не зашиваются в строки. */
@Composable
fun metricNorm(id: MetricId): String = when (id) {
    MetricId.FREE_CASH_FLOW -> stringResource(
        R.string.metric_fcf_norm,
        Benchmarks.SAVINGS_RATE_MIN_PERCENT,
        Benchmarks.SAVINGS_RATE_MAX_PERCENT,
    )
    MetricId.SAVINGS_RATE -> stringResource(
        R.string.metric_savings_norm,
        Benchmarks.SAVINGS_RATE_MIN_PERCENT,
        Benchmarks.SAVINGS_RATE_MAX_PERCENT,
    )
    MetricId.DEBT_LOAD -> stringResource(
        R.string.metric_debt_norm,
        Benchmarks.DEBT_LOAD_NORM_PERCENT,
        Benchmarks.DEBT_LOAD_RISK_PERCENT,
    )
    MetricId.CUSHION_MONTHS -> stringResource(
        R.string.metric_cushion_norm,
        Benchmarks.CUSHION_MIN_MONTHS,
        Benchmarks.CUSHION_MAX_MONTHS,
    )
    MetricId.MANDATORY_SHARE -> stringResource(R.string.metric_mandatory_norm, Benchmarks.MANDATORY_SHARE_NORM_PERCENT)
    MetricId.NET_WORTH -> stringResource(R.string.metric_net_worth_norm)
}

/** Короткая строка нормы для карточки. */
@Composable
fun metricShortNorm(id: MetricId): String = when (id) {
    MetricId.FREE_CASH_FLOW -> stringResource(
        R.string.metric_fcf_short_norm,
        Benchmarks.SAVINGS_RATE_MIN_PERCENT,
        Benchmarks.SAVINGS_RATE_MAX_PERCENT,
    )
    MetricId.SAVINGS_RATE -> stringResource(
        R.string.metric_savings_short_norm,
        Benchmarks.SAVINGS_RATE_MIN_PERCENT,
        Benchmarks.SAVINGS_RATE_MAX_PERCENT,
    )
    MetricId.DEBT_LOAD -> stringResource(R.string.metric_debt_short_norm, Benchmarks.DEBT_LOAD_NORM_PERCENT)
    MetricId.CUSHION_MONTHS -> stringResource(
        R.string.metric_cushion_short_norm,
        Benchmarks.CUSHION_MIN_MONTHS,
        Benchmarks.CUSHION_MAX_MONTHS,
    )
    MetricId.MANDATORY_SHARE -> stringResource(
        R.string.metric_mandatory_short_norm,
        Benchmarks.MANDATORY_SHARE_NORM_PERCENT,
    )
    MetricId.NET_WORTH -> stringResource(R.string.metric_net_worth_short_norm)
}

@StringRes
fun statusLabel(status: MetricStatus): Int = when (status) {
    MetricStatus.GOOD -> R.string.status_good
    MetricStatus.ATTENTION -> R.string.status_attention
    MetricStatus.RISK -> R.string.status_risk
    MetricStatus.NEUTRAL -> R.string.status_neutral
}

/** Значок статуса: форма, а не только цвет, различает состояния. */
fun statusGlyph(status: MetricStatus): String = when (status) {
    MetricStatus.GOOD -> "✓"
    MetricStatus.ATTENTION -> "!"
    MetricStatus.RISK -> "✕"
    MetricStatus.NEUTRAL -> "–"
}
