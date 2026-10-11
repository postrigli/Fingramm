package app.fingramm.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fingramm.R

private val ButtonPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
private const val MIN_FONT_SP = 9f
private const val SHRINK_STEP = 0.92f

/** «Подтвердить» длиннее остальных подписей, поэтому ей достаётся чуть больше места. */
private const val CONFIRM_WEIGHT = 1.3f

/**
 * Кнопки карточки в листе ожидающих, слева направо: «Отменить» · «Изменить» · «Подтвердить» (как в уведомлении).
 * Три кнопки всегда в одной строке: ширина делится по длине подписи, а если подпись всё равно не помещается
 * (узкий экран, крупный шрифт), она уменьшается, но никогда не переносится.
 */
@Composable
internal fun PendingActions(
    enabled: Boolean,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onCancel,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            contentPadding = ButtonPadding,
        ) { OneLineLabel(stringResource(R.string.pending_cancel)) }
        OutlinedButton(
            onClick = onEdit,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            contentPadding = ButtonPadding,
        ) { OneLineLabel(stringResource(R.string.pending_edit)) }
        Button(
            onClick = onConfirm,
            enabled = enabled,
            modifier = Modifier.weight(CONFIRM_WEIGHT),
            contentPadding = ButtonPadding,
        ) { OneLineLabel(stringResource(R.string.pending_confirm)) }
    }
}

/** Подпись строго в одну строку: при нехватке ширины шрифт уменьшается шагами, пока текст не поместится. */
@Composable
private fun OneLineLabel(text: String) {
    val base = MaterialTheme.typography.labelLarge.fontSize
    var size by remember(text, base) { mutableStateOf<TextUnit>(base) }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontSize = size,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            if (result.didOverflowWidth && size.value > MIN_FONT_SP) size = (size.value * SHRINK_STEP).sp
        },
    )
}
