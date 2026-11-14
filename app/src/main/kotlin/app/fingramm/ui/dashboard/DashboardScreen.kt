@file:OptIn(ExperimentalMaterial3Api::class)

package app.fingramm.ui.dashboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.money.Money
import app.fingramm.domain.stats.MonthTotals
import app.fingramm.ui.charts.ChartSegment
import app.fingramm.ui.charts.DonutChart
import app.fingramm.ui.charts.IncomeExpenseBarChart
import app.fingramm.ui.charts.MonthBar
import app.fingramm.ui.common.InfoCard
import app.fingramm.ui.common.LoadingBox
import app.fingramm.ui.common.PeriodSwitcher
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.formatDate
import app.fingramm.ui.common.formatMonthShort
import app.fingramm.ui.common.formatPeriod
import app.fingramm.ui.theme.ChartPalette
import app.fingramm.ui.theme.LocalStatusColors
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DashboardScreen(
    onOpenGoals: () -> Unit,
    onOpenCategory: (categoryId: Long, month: YearMonth) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.dashboard_title)) }) },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val money = { minor: Long -> Money.format(minor, state.currency) }
        ScreenColumn(Modifier.padding(padding)) {
            BalanceCard(state.balanceMinor, money)
            PeriodSwitcher(
                label = formatPeriod(state.month, state.startDay),
                canGoNext = state.canGoNext,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
            if (!state.totals.hasData) {
                InfoCard {
                    Text(stringResource(R.string.dashboard_fact_empty), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                TotalsCard(state.totals, money)
                if (state.month == state.currentMonth) {
                    Text(
                        stringResource(R.string.dashboard_fact_partial),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            StructureSection(state, money, onOpenCategory)
            BarsSection(state, money)
            GoalsSection(state, money, onOpenGoals)

            Text(
                stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.showDisclaimer && !state.loading) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDisclaimer,
            title = { Text(stringResource(R.string.disclaimer_title)) },
            text = { Text(stringResource(R.string.disclaimer_full)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissDisclaimer,
                ) { Text(stringResource(R.string.disclaimer_ok)) }
            },
        )
    }
}

/** Сколько денег у пользователя сейчас (начальный остаток плюс все операции). */
@Composable
private fun BalanceCard(balanceMinor: Long, money: (Long) -> String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.dashboard_balance_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(money(balanceMinor), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.dashboard_balance_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Доходы, расходы и остаток выбранного месяца. */
@Composable
private fun TotalsCard(
    totals: MonthTotals,
    money: (Long) -> String,
) {
    val colors = LocalStatusColors.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TotalsLine(stringResource(R.string.income), "+" + money(totals.incomeMinor), colors.income)
            TotalsLine(stringResource(R.string.expenses), "−" + money(totals.expensesMinor), colors.expense)
            HorizontalDivider()
            TotalsLine(
                stringResource(R.string.balance),
                (if (totals.balanceMinor > 0) "+" else "") + money(totals.balanceMinor),
                MaterialTheme.colorScheme.onSurface,
                bold = true,
            )
        }
    }
}

@Composable
private fun TotalsLine(
    label: String,
    value: String,
    color: Color,
    bold: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
        )
    }
}

@Composable
private fun StructureSection(
    state: DashboardUiState,
    money: (Long) -> String,
    onOpenCategory: (Long, YearMonth) -> Unit,
) {
    val slices = state.structure.filter { it.amountMinor > 0 }
    SectionTitle(stringResource(R.string.dashboard_structure_title))
    if (slices.isEmpty()) {
        InfoCard {
            Text(
                stringResource(R.string.dashboard_structure_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    val palette = if (isSystemInDarkTheme()) ChartPalette.dark else ChartPalette.light
    val others = stringResource(R.string.structure_others)
    val segments =
        slices.mapIndexed { index, slice ->
            val label = if (slice.categoryId == null) {
                others
            } else {
                listOfNotNull(
                    slice.icon,
                    slice.name,
                ).joinToString(" ")
            }
            val open = slice.categoryId?.let { id -> { onOpenCategory(id, state.month) } }
            ChartSegment(label, slice.amountMinor, palette[index % palette.size], open)
        }
    val total = slices.sumOf { it.amountMinor }
    val summary =
        stringResource(R.string.dashboard_structure_cd) + ": " +
            segments.joinToString("; ") { "${it.label} ${money(it.value)}" }
    DonutChart(
        segments = segments,
        centerTitle = stringResource(R.string.dashboard_structure_center_fact),
        centerValue = money(total),
        formatValue = money,
        summary = summary,
    )
    Text(
        stringResource(R.string.dashboard_structure_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BarsSection(
    state: DashboardUiState,
    money: (Long) -> String,
) {
    SectionTitle(stringResource(R.string.dashboard_bars_title))
    if (state.monthlyBars.none { it.hasData }) {
        InfoCard { Text(stringResource(R.string.dashboard_bars_empty), style = MaterialTheme.typography.bodyMedium) }
        return
    }
    val palette = if (isSystemInDarkTheme()) ChartPalette.dark else ChartPalette.light
    val bars = state.monthlyBars.map { MonthBar(formatMonthShort(it.month), it.incomeMinor, it.expensesMinor) }
    val incomeLabel = stringResource(R.string.income)
    val expenseLabel = stringResource(R.string.expenses)
    val summary =
        stringResource(R.string.dashboard_bars_cd) + ": " +
            state.monthlyBars.joinToString("; ") {
                val period = formatPeriod(it.month, state.startDay)
                "$period — $incomeLabel ${money(it.incomeMinor)}, $expenseLabel ${money(it.expensesMinor)}"
            }
    IncomeExpenseBarChart(
        bars = bars,
        // Бирюзовый и янтарный различимы и по тону, и по яркости (в том числе при дальтонизме).
        incomeColor = palette[0],
        expenseColor = palette[2],
        incomeLabel = incomeLabel,
        expenseLabel = expenseLabel,
        summary = summary,
    )
}

@Composable
private fun GoalsSection(
    state: DashboardUiState,
    money: (Long) -> String,
    onOpenGoals: () -> Unit,
) {
    SectionTitle(stringResource(R.string.dashboard_goals_title))
    if (state.goals.isEmpty()) {
        InfoCard {
            Text(stringResource(R.string.dashboard_goals_empty), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenGoals) { Text(stringResource(R.string.dashboard_goals_open)) }
        }
        return
    }
    state.goals.forEach { item ->
        val progress = item.forecast.progress
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.goal.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (progress != null) Text(Money.formatPercent(progress), style = MaterialTheme.typography.bodyMedium)
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Text(
                    stringResource(
                        R.string.goal_saved_of,
                        money(item.goal.savedMinor),
                        money(item.goal.targetMinor ?: 0),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.goal_saved_only, money(item.goal.savedMinor)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.forecast.projectedDate?.let { date ->
                Text(
                    stringResource(R.string.goal_forecast_date, formatDate(date, LocalDate.now())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    TextButton(onClick = onOpenGoals) { Text(stringResource(R.string.dashboard_goals_open)) }
}
