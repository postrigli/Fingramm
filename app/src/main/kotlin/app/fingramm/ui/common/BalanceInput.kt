package app.fingramm.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import app.fingramm.R
import kotlin.math.abs

/**
 * Поле «Остаток средств»: сумма со знаком. Знак выбирается переключателем «В минусе», потому что в поле суммы
 * можно вводить только цифры. Рядом кнопка «?» с объяснением, как посчитать остаток.
 */
@Composable
fun BalanceInput(
    valueMinor: Long?,
    onValueChange: (Long?) -> Unit,
    suffix: String,
    modifier: Modifier = Modifier,
) {
    var negative by remember { mutableStateOf((valueMinor ?: 0L) < 0L) }
    var showHelp by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoneyField(
                valueMinor = valueMinor?.let { abs(it) },
                onValueChange = { amount -> onValueChange(amount?.let { if (negative) -it else it }) },
                label = stringResource(R.string.balance_label),
                modifier = Modifier.weight(1f),
                suffix = suffix,
            )
            HelpButton(
                onClick = { showHelp = true },
                contentDescription = stringResource(R.string.balance_help_cd),
            )
        }
        SwitchRow(
            checked = negative,
            onCheckedChange = { checked ->
                negative = checked
                valueMinor?.let { onValueChange(if (checked) -abs(it) else abs(it)) }
            },
            label = stringResource(R.string.balance_negative),
        )
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.balance_help_title)) },
            text = { Text(stringResource(R.string.balance_help_text), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
