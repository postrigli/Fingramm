package app.fingramm.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.Transaction
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.money.Money
import app.fingramm.domain.stats.LimitStatus
import app.fingramm.ui.common.BackTopBar
import app.fingramm.ui.common.InfoCard
import app.fingramm.ui.common.LoadingBox
import app.fingramm.ui.common.PeriodSwitcher
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.formatDate
import app.fingramm.ui.common.formatPeriod
import app.fingramm.ui.theme.LocalStatusColors
import java.time.LocalDate
import kotlin.math.abs

/** Сводка по категории за период и её операции; нажатие на операцию открывает форму правки. */
@Composable
fun CategoryStatsScreen(
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: CategoryStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val category = state.category
    val title =
        listOfNotNull(category?.icon, category?.name).joinToString(" ")
            .ifBlank { stringResource(R.string.category_unknown) }
    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val money = { minor: Long -> Money.format(minor, state.currency) }
        ScreenColumn(Modifier.padding(padding)) {
            PeriodSwitcher(
                label = formatPeriod(state.month, state.startDay),
                canGoNext = state.canGoNext,
                onPrevious = viewModel::previousPeriod,
                onNext = viewModel::nextPeriod,
            )
            SummaryCard(state, money)
            state.limit?.let { usage -> LimitCard(usage.ratio, usage.status, usage.limit.limitMinor, money) }
            SectionTitle(stringResource(R.string.category_stats_operations))
            if (state.transactions.isEmpty()) {
                InfoCard {
                    Text(stringResource(R.string.category_stats_empty), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                state.transactions.forEach { tx ->
                    OperationRow(tx, state.currency, state.today, onClick = { onOpenTransaction(tx.id) })
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    state: CategoryStatsUiState,
    money: (Long) -> String,
) {
    val isIncome = state.category?.type == TransactionType.INCOME
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (isIncome) R.string.category_stats_received else R.string.category_stats_spent),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(money(state.totalMinor), style = MaterialTheme.typography.headlineMedium)
            state.sharePercent?.let { share ->
                val whole = if (isIncome) {
                    R.string.category_stats_share_income
                } else {
                    R.string.category_stats_share_expense
                }
                Text(stringResource(whole, share), style = MaterialTheme.typography.bodyMedium)
            }
            Text(comparison(state, money), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun comparison(
    state: CategoryStatsUiState,
    money: (Long) -> String,
): String {
    if (state.previousMinor == 0L && state.totalMinor == 0L) return stringResource(R.string.category_stats_no_data)
    val delta = state.totalMinor - state.previousMinor
    val sign =
        if (delta > 0) {
            "+"
        } else if (delta < 0) {
            "−"
        } else {
            ""
        }
    return stringResource(
        R.string.category_stats_vs_previous,
        sign + money(abs(delta)),
        money(state.previousMinor),
    )
}

@Composable
private fun LimitCard(
    ratio: Double,
    status: LimitStatus,
    limitMinor: Long,
    money: (Long) -> String,
) {
    val colors = LocalStatusColors.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.category_stats_limit, money(limitMinor)),
                style = MaterialTheme.typography.titleSmall,
            )
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color =
                    when (status) {
                        LimitStatus.OK -> colors.income
                        LimitStatus.ATTENTION -> colors.attention
                        LimitStatus.EXCEEDED -> colors.expense
                    },
            )
            Text(Money.formatPercent(ratio), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OperationRow(
    tx: Transaction,
    currency: CurrencyCode,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val colors = LocalStatusColors.current
    val isIncome = tx.type == TransactionType.INCOME
    val amount = (if (isIncome) "+" else "−") + Money.format(tx.amountMinor, currency)
    val date = formatDate(tx.date, today)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$date, $amount, ${tx.note}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(date, style = MaterialTheme.typography.bodyLarge)
            if (tx.note.isNotBlank()) {
                Text(
                    tx.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            amount,
            style = MaterialTheme.typography.titleSmall,
            color = if (isIncome) colors.income else colors.expense,
        )
    }
}
