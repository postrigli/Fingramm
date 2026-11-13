package app.fingramm.ui.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.model.Category
import app.fingramm.domain.money.Money
import app.fingramm.domain.report.CategorySpend
import app.fingramm.domain.report.Change
import app.fingramm.ui.common.BackTopBar
import app.fingramm.ui.common.InfoCard
import app.fingramm.ui.common.LoadingBox
import app.fingramm.ui.common.PeriodSwitcher
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.formatPeriod
import app.fingramm.ui.theme.LocalStatusColors
import java.time.YearMonth
import kotlin.math.abs

/** Отчёт месяца: итоги с прошлым периодом, пять самых затратных категорий и самый большой рост. */
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onOpenCategory: (categoryId: Long, period: YearMonth) -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar(stringResource(R.string.report_title), onBack) }) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val money = { minor: Long -> Money.format(minor, state.currency) }
        ScreenColumn(Modifier.padding(padding)) {
            PeriodSwitcher(
                label = formatPeriod(state.period, state.startDay),
                canGoNext = state.canGoNext,
                onPrevious = viewModel::previousPeriod,
                onNext = viewModel::nextPeriod,
            )
            if (!state.report.hasData) {
                InfoCard { Text(stringResource(R.string.report_empty), style = MaterialTheme.typography.bodyMedium) }
                return@ScreenColumn
            }
            TotalsSection(state, money)
            TopSection(state, money) { id -> onOpenCategory(id, state.period) }
            GrowthSection(state, money) { id -> onOpenCategory(id, state.period) }
        }
    }
}

@Composable
private fun TotalsSection(
    state: ReportUiState,
    money: (Long) -> String,
) {
    val colors = LocalStatusColors.current
    val totals = state.report.totals
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TotalsLine(stringResource(R.string.income), "+" + money(totals.incomeMinor), colors.income)
            ChangeLine(state.report.incomeChange, money)
            TotalsLine(stringResource(R.string.expenses), "−" + money(totals.expensesMinor), colors.expense)
            ChangeLine(state.report.expenseChange, money)
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
private fun ChangeLine(
    change: Change?,
    money: (Long) -> String,
) {
    if (change == null) return
    val sign =
        if (change.deltaMinor > 0) {
            "+"
        } else if (change.deltaMinor < 0) {
            "−"
        } else {
            ""
        }
    val percent = change.percent?.let { " ($sign${abs(it)}%)" }.orEmpty()
    Text(
        stringResource(R.string.report_vs_previous, sign + money(abs(change.deltaMinor)), percent),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TopSection(
    state: ReportUiState,
    money: (Long) -> String,
    onOpen: (Long) -> Unit,
) {
    val top = state.report.topCategories
    if (top.isEmpty()) return
    SectionTitle(stringResource(R.string.report_top_title, top.size))
    top.forEach { spend ->
        CategoryRow(
            category = state.categories[spend.categoryId],
            amount = money(spend.amountMinor),
            detail = spend.sharePercent?.let { stringResource(R.string.report_share, it) },
            onClick = { onOpen(spend.categoryId) },
        )
    }
}

@Composable
private fun GrowthSection(
    state: ReportUiState,
    money: (Long) -> String,
    onOpen: (Long) -> Unit,
) {
    val growth: CategorySpend = state.report.biggestGrowth ?: return
    SectionTitle(stringResource(R.string.report_growth_title))
    Card(Modifier.fillMaxWidth()) {
        CategoryRow(
            category = state.categories[growth.categoryId],
            amount = "+" + money(growth.deltaMinor),
            detail = stringResource(R.string.report_growth_was, money(growth.previousMinor)),
            onClick = { onOpen(growth.categoryId) },
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category?,
    amount: String,
    detail: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(category?.icon ?: "•", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                category?.name ?: stringResource(R.string.category_unknown),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(amount, style = MaterialTheme.typography.titleSmall)
    }
}
