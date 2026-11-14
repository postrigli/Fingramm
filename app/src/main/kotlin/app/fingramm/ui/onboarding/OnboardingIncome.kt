@file:OptIn(ExperimentalLayoutApi::class)

package app.fingramm.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.fingramm.R
import app.fingramm.ui.common.IntField
import app.fingramm.ui.common.MoneyField
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.weekdayShort

private const val DAYS_IN_WEEK = 7

/** Доходы на стартовом экране: основной доход или зарплата и, по желанию, второй (вторая работа, аренда). */
@Composable
fun IncomeSection(state: OnboardingUiState, onUpdate: ((OnboardingUiState) -> OnboardingUiState) -> Unit) {
    SectionTitle(stringResource(R.string.onboarding_income_title))
    Text(stringResource(R.string.onboarding_income_hint), style = MaterialTheme.typography.bodySmall)
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = state.primaryIncome.enabled,
                role = Role.Checkbox,
                onValueChange = { checked ->
                    onUpdate { it.copy(primaryIncome = it.primaryIncome.copy(enabled = checked)) }
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = state.primaryIncome.enabled, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(stringResource(R.string.income_primary), style = MaterialTheme.typography.bodyLarge)
    }
    if (state.primaryIncome.enabled) {
        IncomeFields(
            row = state.primaryIncome,
            showErrors = state.showErrors,
            onChange = { changed -> onUpdate { it.copy(primaryIncome = changed) } },
        )
    }
    val second = state.secondIncome
    if (second == null) {
        TextButton(
            onClick = { onUpdate { it.copy(secondIncome = IncomeRow(kind = IncomeKind.RENT, enabled = true)) } },
        ) { Text(stringResource(R.string.income_add_second)) }
    } else {
        SecondIncome(
            row = second,
            showErrors = state.showErrors,
            onChange = { changed -> onUpdate { it.copy(secondIncome = changed) } },
            onRemove = { onUpdate { it.copy(secondIncome = null) } },
        )
    }
}

@Composable
private fun SecondIncome(row: IncomeRow, showErrors: Boolean, onChange: (IncomeRow) -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.income_second_title),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove) { Text(stringResource(R.string.income_remove)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IncomeKind.entries.forEach { kind ->
                FilterChip(
                    selected = row.kind == kind,
                    onClick = { onChange(row.copy(kind = kind)) },
                    label = { Text(stringResource(kind.titleRes)) },
                )
            }
        }
        IncomeFields(row = row, showErrors = showErrors, onChange = onChange)
    }
}

/** Частота, сумма одной выплаты и дни выплат. */
@Composable
private fun IncomeFields(row: IncomeRow, showErrors: Boolean, onChange: (IncomeRow) -> Unit) {
    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IncomeFrequency.entries.forEach { frequency ->
                FilterChip(
                    selected = row.frequency == frequency,
                    onClick = { onChange(row.copy(frequency = frequency)) },
                    label = { Text(stringResource(frequencyLabel(frequency))) },
                )
            }
        }
        MoneyField(
            valueMinor = row.amountMinor,
            onValueChange = { onChange(row.copy(amountMinor = it)) },
            label = stringResource(R.string.income_amount),
            isError = showErrors && !row.amountValid,
        )
        when (row.frequency) {
            IncomeFrequency.MONTHLY -> IntField(
                value = row.firstDay,
                onValueChange = { onChange(row.copy(firstDay = it)) },
                label = stringResource(R.string.onboarding_day),
                isError = showErrors && !row.firstDayValid,
            )
            IncomeFrequency.TWICE_MONTHLY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField(
                    value = row.firstDay,
                    onValueChange = { onChange(row.copy(firstDay = it)) },
                    label = stringResource(R.string.income_first_day),
                    modifier = Modifier.weight(1f),
                    isError = showErrors && !row.firstDayValid,
                )
                IntField(
                    value = row.secondDay,
                    onValueChange = { onChange(row.copy(secondDay = it)) },
                    label = stringResource(R.string.income_second_day),
                    modifier = Modifier.weight(1f),
                    isError = showErrors && !row.secondDayValid,
                )
            }
            IncomeFrequency.WEEKLY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (day in 1..DAYS_IN_WEEK) {
                    FilterChip(
                        selected = row.weekday == day,
                        onClick = { onChange(row.copy(weekday = day)) },
                        label = { Text(weekdayShort(day)) },
                    )
                }
            }
        }
    }
}

private fun frequencyLabel(frequency: IncomeFrequency): Int = when (frequency) {
    IncomeFrequency.MONTHLY -> R.string.income_freq_month
    IncomeFrequency.TWICE_MONTHLY -> R.string.income_freq_twice
    IncomeFrequency.WEEKLY -> R.string.income_freq_week
}
