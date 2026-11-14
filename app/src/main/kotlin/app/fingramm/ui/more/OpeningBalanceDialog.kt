package app.fingramm.ui.more

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.fingramm.R
import app.fingramm.ui.common.BalanceInput
import app.fingramm.ui.common.LocalCurrency

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

/** Показывает диалог, пока [show] истинно: условие вынесено сюда, чтобы не усложнять экран настроек. */
@Composable
fun OpeningBalanceDialogHost(show: Boolean, currentMinor: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    if (show) OpeningBalanceDialog(currentMinor, onSave, onDismiss)
}
