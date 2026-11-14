package app.fingramm.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fingramm.R
import app.fingramm.domain.money.Money
import app.fingramm.ui.common.BalanceInput
import app.fingramm.ui.common.LocalCurrency
import app.fingramm.ui.common.SectionTitle

/**
 * Раздел настроек «Остаток средств»: изменить начальный остаток и сверить остаток с банком.
 * [trackedMinor] — итог всех внесённых операций (доходы минус расходы): сумма на сегодня = начальный остаток + он.
 */
@Composable
fun OpeningBalanceSection(
    openingMinor: Long,
    trackedMinor: Long,
    onSetOpening: (Long) -> Unit,
    onReconcile: (actualMinor: Long) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var reconciling by remember { mutableStateOf(false) }
    val currency = LocalCurrency.current
    SectionTitle(stringResource(R.string.settings_balance_title))
    Text(stringResource(R.string.settings_balance_hint), style = MaterialTheme.typography.bodySmall)
    Text(
        stringResource(R.string.reconcile_now, Money.format(openingMinor + trackedMinor, currency)),
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(onClick = { reconciling = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.reconcile_button))
    }
    OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_balance_button, Money.format(openingMinor, currency)))
    }
    if (editing) {
        OpeningBalanceDialog(
            currentMinor = openingMinor,
            onSave = {
                onSetOpening(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
    if (reconciling) {
        ReconcileDialog(
            appBalanceMinor = openingMinor + trackedMinor,
            onSave = {
                onReconcile(it)
                reconciling = false
            },
            onDismiss = { reconciling = false },
        )
    }
}

/** Изменение остатка средств на начало учёта. Пустое поле сохраняется как 0. */
@Composable
fun OpeningBalanceDialog(currentMinor: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf<Long?>(currentMinor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_balance_title)) },
        text = {
            BalanceInput(
                valueMinor = draft,
                onValueChange = { draft = it },
                suffix = LocalCurrency.current.symbol,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(draft ?: 0L) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Сверка с банком: пользователь вводит, сколько денег у него на самом деле; приложение пересчитывает начальный
 * остаток (операции не меняются) и показывает расхождение, которое будет устранено.
 */
@Composable
fun ReconcileDialog(appBalanceMinor: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var actual by remember { mutableStateOf<Long?>(appBalanceMinor) }
    val currency = LocalCurrency.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reconcile_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.reconcile_hint), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.reconcile_now, Money.format(appBalanceMinor, currency)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                BalanceInput(valueMinor = actual, onValueChange = { actual = it }, suffix = currency.symbol)
                actual?.let {
                    Text(
                        stringResource(R.string.reconcile_diff, Money.format(it - appBalanceMinor, currency)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(actual ?: 0L) }) { Text(stringResource(R.string.reconcile_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
