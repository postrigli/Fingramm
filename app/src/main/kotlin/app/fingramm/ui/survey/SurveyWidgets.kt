package app.fingramm.ui.survey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.fingramm.R
import app.fingramm.domain.model.InputMode
import app.fingramm.domain.survey.DraftAmount
import app.fingramm.domain.survey.ExpenseDraft
import app.fingramm.domain.survey.SurveyError
import app.fingramm.domain.survey.SurveyErrorCode
import app.fingramm.domain.survey.SurveyField
import app.fingramm.domain.survey.SurveyValidation
import app.fingramm.ui.common.DatePickerDialogHost
import app.fingramm.ui.common.MoneyField
import app.fingramm.ui.common.SwitchRow
import app.fingramm.ui.common.formatDateFull
import app.fingramm.ui.common.rememberMoneyFormatter
import java.time.LocalDate

/** Текст ошибки валидации поля. */
@Composable
fun SurveyError.message(): String = when (code) {
    SurveyErrorCode.REQUIRED -> stringResource(R.string.error_required)
    SurveyErrorCode.MUST_BE_POSITIVE -> stringResource(R.string.error_must_be_positive)
    SurveyErrorCode.DATE_IN_PAST -> stringResource(R.string.error_date_in_past)
    SurveyErrorCode.OUT_OF_RANGE -> when (field) {
        SurveyField.FAMILY_SIZE ->
            stringResource(
                R.string.error_family_size,
                SurveyValidation.MIN_FAMILY_SIZE,
                SurveyValidation.MAX_FAMILY_SIZE,
            )
        SurveyField.CHILDREN_COUNT -> stringResource(R.string.error_children_count)
        else -> stringResource(R.string.error_out_of_range)
    }
}

/** Первая ошибка поля [field] (для строки списка — с учётом индекса). */
fun List<SurveyError>.forField(field: SurveyField, index: Int? = null): SurveyError? =
    firstOrNull { it.field == field && (index == null || it.index == null || it.index == index) }

/**
 * Редактор списка строк «название + сумма» (источники дохода, активы, расходы по категориям).
 */
@Composable
fun AmountRowsEditor(
    rows: List<DraftAmount>,
    onRowsChange: (List<DraftAmount>) -> Unit,
    nameLabel: String,
    amountLabel: String,
    addLabel: String,
    errors: List<SurveyError>,
    field: SurveyField,
    modifier: Modifier = Modifier,
    minRows: Int = 0,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { index, row ->
            val error = errors.forField(field, index)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { name ->
                        onRowsChange(rows.toMutableList().also { it[index] = row.copy(name = name) })
                    },
                    label = { Text(nameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.Top) {
                    MoneyField(
                        valueMinor = row.amountMinor,
                        onValueChange = { amount ->
                            onRowsChange(rows.toMutableList().also { it[index] = row.copy(amountMinor = amount) })
                        },
                        label = amountLabel,
                        modifier = Modifier.weight(1f),
                        isError = error != null,
                        supportingText = error?.message(),
                        imeAction = ImeAction.Done,
                    )
                    if (rows.size > minRows) {
                        IconButton(onClick = { onRowsChange(rows.toMutableList().also { it.removeAt(index) }) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete_row))
                        }
                    }
                }
                if (index < rows.lastIndex) HorizontalDivider(Modifier.padding(top = 4.dp))
            }
        }
        TextButton(onClick = { onRowsChange(rows + DraftAmount()) }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(addLabel, Modifier.padding(start = 8.dp))
        }
    }
}

/** Ввод расходов: одной суммой или по категориям; для переменных — ещё и «не знаю». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseInput(
    value: ExpenseDraft,
    onChange: (ExpenseDraft) -> Unit,
    presetNames: List<String>,
    allowUnknown: Boolean,
    errors: List<SurveyError>,
    field: SurveyField,
    modifier: Modifier = Modifier,
) {
    val format = rememberMoneyFormatter()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (allowUnknown) {
            SwitchRow(
                checked = value.unknown,
                onCheckedChange = { onChange(value.copy(unknown = it)) },
                label = stringResource(R.string.survey_variable_unknown),
                description = stringResource(R.string.survey_variable_unknown_hint),
            )
        }
        if (!value.unknown) {
            val modes =
                listOf(
                    InputMode.TOTAL to R.string.survey_mode_total,
                    InputMode.BY_CATEGORY to R.string.survey_mode_categories,
                )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = value.mode == mode,
                        onClick = {
                            val items = if (mode == InputMode.BY_CATEGORY && value.items.isEmpty()) {
                                presetNames.map { DraftAmount(it, null) }
                            } else {
                                value.items
                            }
                            onChange(value.copy(mode = mode, items = items))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            val error = errors.forField(field)
            if (value.mode == InputMode.TOTAL) {
                MoneyField(
                    valueMinor = value.totalMinor,
                    onValueChange = { onChange(value.copy(totalMinor = it)) },
                    label = stringResource(R.string.survey_per_month),
                    isError = error != null,
                    supportingText = error?.message(),
                )
            } else {
                AmountRowsEditor(
                    rows = value.items,
                    onRowsChange = { onChange(value.copy(items = it)) },
                    nameLabel = stringResource(R.string.survey_item_name),
                    amountLabel = stringResource(R.string.survey_per_month),
                    addLabel = stringResource(R.string.survey_add_category),
                    errors = emptyList(),
                    field = field,
                )
                if (error != null) {
                    Text(
                        error.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val total = value.items.sumOf { it.amountMinor ?: 0L }
                Text(
                    stringResource(R.string.survey_total_per_month, format(total)),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

/** Поле даты с кнопкой выбора и сбросом. */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    error: SurveyError? = null,
) {
    var picking by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                Text(
                    date?.let { "$label: ${formatDateFull(it)}" } ?: "$label: ${stringResource(R.string.date_not_set)}",
                )
            }
            if (date != null) {
                TextButton(onClick = { onDateChange(null) }) { Text(stringResource(R.string.action_clear)) }
            }
        }
        if (error != null) {
            Text(error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (picking) {
        DatePickerDialogHost(
            initial = date,
            onDismiss = { picking = false },
            onConfirm = {
                onDateChange(it)
                picking = false
            },
        )
    }
}
