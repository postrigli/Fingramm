@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.fingramm.ui.transactions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.model.AccountType
import app.fingramm.domain.model.TransactionType
import app.fingramm.ui.common.BackTopBar
import app.fingramm.ui.common.ConfirmDialog
import app.fingramm.ui.common.DatePickerDialogHost
import app.fingramm.ui.common.LoadingBox
import app.fingramm.ui.common.LocalCurrency
import app.fingramm.ui.common.MoneyField
import app.fingramm.ui.common.PrimaryButton
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.formatDateFull
import java.time.LocalDate

@StringRes
fun accountLabel(type: AccountType): Int = when (type) {
    AccountType.CASH -> R.string.account_cash
    AccountType.CARD -> R.string.account_card
    AccountType.SAVINGS -> R.string.account_savings
}

/** Быстрая форма операции: сумма (с автофокусом) → категория → «Сохранить» — три касания для типичной траты. */
@Composable
fun TransactionEditScreen(onBack: () -> Unit, viewModel: TransactionEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onBack() } }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val title = if (state.editingId == null) R.string.transaction_add else R.string.transaction_edit

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            BackTopBar(
                title = stringResource(title),
                onBack = onBack,
                actions = {
                    if (state.editingId != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.padding(16.dp),
            )
        },
    ) { padding ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LaunchedEffect(state.loaded) { if (state.editingId == null) focus.requestFocus() }
        ScreenColumn(Modifier.padding(padding)) {
            val types = listOf(TransactionType.EXPENSE to R.string.expense, TransactionType.INCOME to R.string.income)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size),
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            MoneyField(
                valueMinor = state.amountMinor,
                onValueChange = viewModel::setAmount,
                label = stringResource(R.string.transaction_amount),
                modifier = Modifier.focusRequester(focus),
                isError = state.amountError,
                supportingText = if (state.amountError) stringResource(R.string.error_amount_positive) else null,
                suffix = LocalCurrency.current.symbol,
            )
            SectionTitle(stringResource(R.string.transaction_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { category ->
                    FilterChip(
                        selected = state.categoryId == category.id,
                        onClick = { viewModel.setCategory(category.id) },
                        label = { Text("${category.icon} ${category.name}") },
                    )
                }
            }
            if (state.categoryError) {
                Text(
                    stringResource(R.string.error_category_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                val label = if (state.date == LocalDate.now()) {
                    stringResource(R.string.date_today) + ", " + formatDateFull(state.date)
                } else {
                    formatDateFull(state.date)
                }
                Text(stringResource(R.string.transaction_date) + ": " + label)
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.transaction_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle(stringResource(R.string.transaction_account))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.accountType == null,
                    onClick = { viewModel.setAccount(null) },
                    label = { Text(stringResource(R.string.account_none)) },
                )
                AccountType.entries.forEach { account ->
                    FilterChip(
                        selected = state.accountType == account,
                        onClick = { viewModel.setAccount(account) },
                        label = { Text(stringResource(accountLabel(account))) },
                    )
                }
            }
        }
    }

    if (pickingDate) {
        DatePickerDialogHost(
            initial = state.date,
            onDismiss = { pickingDate = false },
            onConfirm = {
                viewModel.setDate(it)
                pickingDate = false
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.transaction_delete_title),
            message = stringResource(R.string.transaction_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
