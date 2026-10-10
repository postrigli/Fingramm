package app.fingramm.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fingramm.domain.money.Money

/** Сегмент кольцевой диаграммы. [value] — в минимальных единицах валюты. */
data class ChartSegment(val label: String, val value: Long, val color: Color)

/**
 * Кольцевая диаграмма, нарисованная на Canvas (без внешних библиотек графиков — см. DECISIONS.md).
 * Под диаграммой — легенда с суммами и процентами, поэтому смысл не передаётся одним цветом.
 */
@Composable
fun DonutChart(
    segments: List<ChartSegment>,
    centerTitle: String,
    centerValue: String,
    formatValue: (Long) -> String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val positive = segments.filter { it.value > 0 }
    val total = positive.sumOf { it.value }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(176.dp)
                .semantics { contentDescription = summary },
            contentAlignment = Alignment.Center,
        ) {
            val track = MaterialTheme.colorScheme.surfaceVariant
            Canvas(Modifier.size(176.dp).clearAndSetSemantics { }) {
                val strokeWidth = 28.dp.toPx()
                val inset = strokeWidth / 2
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(inset, inset)
                drawArc(track, 0f, FULL_CIRCLE, false, topLeft, arcSize, style = Stroke(strokeWidth))
                if (total > 0) {
                    var start = START_ANGLE
                    positive.forEach { segment ->
                        val sweep = FULL_CIRCLE * segment.value / total
                        val gap = if (positive.size > 1) GAP_DEGREES else 0f
                        drawArc(
                            segment.color,
                            start,
                            (sweep - gap).coerceAtLeast(MIN_SWEEP),
                            false,
                            topLeft,
                            arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                        )
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
                Text(
                    centerTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(centerValue, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            positive.forEach { segment ->
                LegendRow(
                    color = segment.color,
                    label = segment.label,
                    value = formatValue(segment.value),
                    percent = Money.formatPercent(segment.value.toDouble() / total),
                )
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String, percent: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(label, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
        Text(
            percent,
            Modifier.width(64.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

/** Пара столбцов за месяц. */
data class MonthBar(val label: String, val incomeMinor: Long, val expenseMinor: Long)

/** Столбчатая диаграмма «доходы vs расходы по месяцам». */
@Composable
fun IncomeExpenseBarChart(
    bars: List<MonthBar>,
    incomeColor: Color,
    expenseColor: Color,
    incomeLabel: String,
    expenseLabel: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val maxValue = bars.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) }?.coerceAtLeast(1L) ?: 1L
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth().semantics { contentDescription = summary }) {
        Canvas(Modifier.fillMaxWidth().height(160.dp).clearAndSetSemantics { }) {
            val groupWidth = size.width / bars.size.coerceAtLeast(1)
            val barWidth = (groupWidth * BAR_GROUP_FILL) / 2
            val baseline = size.height
            drawLine(gridColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())
            val radius = CornerRadius(4.dp.toPx())
            bars.forEachIndexed { index, bar ->
                val groupStart = index * groupWidth + groupWidth * (1 - BAR_GROUP_FILL) / 2
                listOf(bar.incomeMinor to incomeColor, bar.expenseMinor to expenseColor).forEachIndexed {
                        i,
                        (value, color),
                    ->
                    val barHeight = (size.height * value / maxValue).coerceAtLeast(if (value > 0) 2.dp.toPx() else 0f)
                    drawRoundRect(
                        color,
                        Offset(groupStart + i * barWidth, baseline - barHeight),
                        Size(barWidth - 2.dp.toPx(), barHeight),
                        radius,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { }) {
            bars.forEach {
                Text(
                    it.label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.padding(top = 8.dp).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LegendSwatch(incomeColor, incomeLabel)
            LegendSwatch(expenseColor, expenseLabel)
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 12.dp, height = 12.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

private const val FULL_CIRCLE = 360f
private const val START_ANGLE = -90f
private const val GAP_DEGREES = 2f
private const val MIN_SWEEP = 1f
private const val BAR_GROUP_FILL = 0.7f
