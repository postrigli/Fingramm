@file:OptIn(ExperimentalMaterial3Api::class)

package app.fingramm.ui.dashboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.metrics.MetricId
import app.fingramm.domain.metrics.MetricResult
import app.fingramm.domain.metrics.MetricStatus
import app.fingramm.domain.metrics.VariableSource
import app.fingramm.domain.money.Money
import app.fingramm.ui.charts.ChartSegment
import app.fingramm.ui.charts.DonutChart
import app.fingramm.ui.charts.IncomeExpenseBarChart
import app.fingramm.ui.charts.MonthBar
import app.fingramm.ui.common.HelpButton
import app.fingramm.ui.common.InfoCard
import app.fingramm.ui.common.LoadingBox
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.formatDate
import app.fingramm.ui.common.formatDecimal
import app.fingramm.ui.common.formatMonth
import app.fingramm.ui.common.formatMonthShort
import app.fingramm.ui.theme.ChartPalette
import app.fingramm.ui.theme.LocalStatusColors
import java.time.LocalDate

@Composable
fun DashboardScreen(onOpenGoals: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var helpFor by remember { mutableStateOf<MetricId?>(null) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.dashboard_title)) }) },
    ) { padding ->
        if (state.loading || state.report == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val report = checkNotNull(state.report)
        val money = { minor: Long -> Money.format(minor, state.currency) }
        ScreenColumn(Modifier.padding(padding)) {
            ModeSwitch(state.mode, viewModel::setMode)
            if (state.mode == DashboardMode.FACT) {
                MonthSwitcher(
                    label = formatMonth(state.month),
                    canGoNext = state.canGoNext,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
                if (!state.factHasData) {
                    InfoCard {
                        Text(
                            stringResource(R.string.dashboard_fact_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else if (state.month == state.currentMonth) {
                    Text(
                        stringResource(R.string.dashboard_fact_partial),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                PlanNotices(state.variableSource, state.debtBalanceMissing)
            }

            report.results.forEach { result ->
                MetricCard(result = result, money = money, onHelp = { helpFor = result.id })
            }

            StructureSection(state, money)
            BarsSection(state, money)
            GoalsSection(state, money, onOpenGoals)

            Text(
                stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    helpFor?.let { id -> MetricHelpSheet(id = id, onDismiss = { helpFor = null }) }
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

@Composable
private fun ModeSwitch(mode: DashboardMode, onChange: (DashboardMode) -> Unit) {
    val options = listOf(DashboardMode.PLAN to R.string.mode_plan, DashboardMode.FACT to R.string.mode_fact)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun MonthSwitcher(label: String, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.month_previous),
            )
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.month_next))
        }
    }
}

@Composable
private fun PlanNotices(variableSource: VariableSource, debtBalanceMissing: Boolean) {
    when (variableSource) {
        VariableSource.UNKNOWN -> InfoCard {
            Text(stringResource(R.string.dashboard_variable_unknown), style = MaterialTheme.typography.bodyMedium)
        }
        VariableSource.ESTIMATED_FROM_FACTS -> InfoCard {
            Text(stringResource(R.string.dashboard_variable_estimated), style = MaterialTheme.typography.bodyMedium)
        }
        VariableSource.DECLARED -> Unit
    }
    if (debtBalanceMissing) {
        InfoCard {
            Text(
                stringResource(R.string.dashboard_debt_balance_missing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MetricCard(result: MetricResult, money: (Long) -> String, onHelp: () -> Unit) {
    val help = metricHelp(result.id)
    val title = stringResource(help.title)
    val value = metricValueText(result, money)
    val status = stringResource(statusLabel(result.status))
    val description = "$title: $value. $status"
    Card(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                HelpButton(onClick = onHelp, contentDescription = stringResource(R.string.metric_help_cd, title))
            }
            Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(result.status)
            }
            Text(
                metricShortNorm(result.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp, end = 12.dp)
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun metricValueText(result: MetricResult, money: (Long) -> String): String = when {
    result.moneyMinor != null -> money(result.moneyMinor ?: 0L)
    result.ratio != null -> Money.formatPercent(result.ratio ?: 0.0)
    result.months != null -> stringResource(R.string.months_format, formatDecimal(result.months ?: 0.0))
    else -> stringResource(R.string.value_undefined)
}

/** Метка статуса: цвет + значок + слово. */
@Composable
fun StatusChip(status: MetricStatus, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    val (container, content) = when (status) {
        MetricStatus.GOOD -> colors.goodContainer to colors.onGood
        MetricStatus.ATTENTION -> colors.attentionContainer to colors.onAttention
        MetricStatus.RISK -> colors.riskContainer to colors.onRisk
        MetricStatus.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            "${statusGlyph(status)} ${stringResource(statusLabel(status))}",
            color = content,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StructureSection(state: DashboardUiState, money: (Long) -> String) {
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
    val segments = slices.mapIndexed { index, slice ->
        ChartSegment(structureLabel(slice), slice.amountMinor, palette[index % palette.size])
    }
    val total = slices.sumOf { it.amountMinor }
    val summary = stringResource(R.string.dashboard_structure_cd) + ": " +
        segments.joinToString("; ") { "${it.label} ${money(it.value)}" }
    DonutChart(
        segments = segments,
        centerTitle = stringResource(
            when (state.mode) {
                DashboardMode.PLAN -> R.string.dashboard_structure_center_plan
                DashboardMode.FACT -> R.string.dashboard_structure_center_fact
            },
        ),
        centerValue = money(total),
        formatValue = money,
        summary = summary,
    )
}

@Composable
private fun structureLabel(slice: StructureSlice): String = when (slice.key) {
    StructureKey.MANDATORY -> stringResource(R.string.structure_mandatory)
    StructureKey.VARIABLE -> stringResource(R.string.structure_variable)
    StructureKey.DEBT -> stringResource(R.string.structure_debt)
    StructureKey.FREE -> stringResource(R.string.structure_free)
    StructureKey.OTHERS -> stringResource(R.string.structure_others)
    StructureKey.CATEGORY -> listOfNotNull(slice.icon, slice.name).joinToString(" ")
}

@Composable
private fun BarsSection(state: DashboardUiState, money: (Long) -> String) {
    SectionTitle(stringResource(R.string.dashboard_bars_title))
    if (state.monthlyBars.none { it.hasData }) {
        InfoCard { Text(stringResource(R.string.dashboard_bars_empty), style = MaterialTheme.typography.bodyMedium) }
        return
    }
    val palette = if (isSystemInDarkTheme()) ChartPalette.dark else ChartPalette.light
    val bars = state.monthlyBars.map { MonthBar(formatMonthShort(it.month), it.incomeMinor, it.expensesMinor) }
    val incomeLabel = stringResource(R.string.income)
    val expenseLabel = stringResource(R.string.expenses)
    val summary = stringResource(R.string.dashboard_bars_cd) + ": " + state.monthlyBars.joinToString("; ") {
        "${formatMonth(it.month)} — $incomeLabel ${money(it.incomeMinor)}, $expenseLabel ${money(it.expensesMinor)}"
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
private fun GoalsSection(state: DashboardUiState, money: (Long) -> String, onOpenGoals: () -> Unit) {
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

/** Подсказка «?»: что это, как считается, норма, 1–3 совета. */
@Composable
private fun MetricHelpSheet(id: MetricId, onDismiss: () -> Unit) {
    val help = metricHelp(id)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(help.title), style = MaterialTheme.typography.headlineSmall)
            HelpBlock(R.string.metric_help_what, stringResource(help.what))
            HelpBlock(R.string.metric_help_how, stringResource(help.how))
            HelpBlock(R.string.metric_help_norm, metricNorm(id))
            SectionTitle(stringResource(R.string.metric_help_tips))
            help.tips.forEach { tip ->
                Row {
                    Text("•", Modifier.padding(end = 8.dp))
                    Text(stringResource(tip), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HelpBlock(title: Int, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(title))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
