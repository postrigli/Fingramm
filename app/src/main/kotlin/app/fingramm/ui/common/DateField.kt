package app.fingramm.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
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
import app.fingramm.R
import java.time.LocalDate

/** Поле даты с кнопкой выбора и сбросом. */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
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
