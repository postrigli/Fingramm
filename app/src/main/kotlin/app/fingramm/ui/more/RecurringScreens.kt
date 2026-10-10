@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.fingramm.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.model.AccountType
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.money.Money
import app.fingramm.ui.common.BackTopBar
import app.fingramm.ui.common.ConfirmDialog
import app.fingramm.ui.common.EmptyState
import app.fingramm.ui.common.IntField
import app.fingramm.ui.common.LoadingBox
import app.fingramm.ui.common.LocalCurrency
import app.fingramm.ui.common.MoneyField
import app.fingramm.ui.common.PrimaryButton
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.SwitchRow
import app.fingramm.ui.transactions.accountLabel

@Composable
fun RecurringListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: RecurringListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.recurring_title), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.recurring_add))
            }
        },
    ) { padding ->
        if (state.templates.isEmpty()) {
            EmptyState(
                stringResource(R.string.recurring_empty_title),
                stringResource(R.string.recurring_empty_message),
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(state.templates, key = { it.id }) { template ->
                    val category = state.categories[template.categoryId]
                    val sign = if (template.type == TransactionType.INCOME) "+" else "−"
                    ListItem(
                        leadingContent = { Text(category?.icon ?: "•") },
                        headlineContent = {
                            Text(
                                "${category?.name.orEmpty()} · $sign${Money.format(
                                    template.amountMinor,
                                    state.currency,
                                )}",
                            )
                        },
                        supportingContent = {
                            val status = if (template.active) {
                                stringResource(R.string.recurring_next, template.nextDueDate.toString())
                            } else {
                                stringResource(R.string.recurring_paused)
                            }
                            Text(
                                listOf(
                                    stringResource(R.string.recurring_day, template.dayOfMonth),
                                    status,
                                    template.note,
                                )
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                            )
                        },
                        modifier = Modifier.clickable { onEdit(template.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun RecurringEditScreen(onBack: () -> Unit, viewModel: RecurringEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onBack() } }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            BackTopBar(
                title = stringResource(
                    if (state.editingId == null) R.string.recurring_add else R.string.recurring_edit,
                ),
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
                stringResource(R.string.action_save),
                onClick = viewModel::save,
                modifier = Modifier.padding(16.dp),
            )
        },
    ) { padding ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
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
                onValueChange = { v -> viewModel.update { it.copy(amountMinor = v) } },
                label = stringResource(R.string.transaction_amount),
                isError = state.amountError,
                supportingText = if (state.amountError) stringResource(R.string.error_amount_positive) else null,
                suffix = LocalCurrency.current.symbol,
            )
            SectionTitle(stringResource(R.string.transaction_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { category ->
                    FilterChip(
                        selected = state.categoryId == category.id,
                        onClick = { viewModel.update { it.copy(categoryId = category.id) } },
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
            IntField(
                value = state.dayOfMonth,
                onValueChange = { v -> viewModel.update { it.copy(dayOfMonth = v) } },
                label = stringResource(R.string.recurring_day_label),
                isError = state.dayError,
                supportingText = stringResource(R.string.recurring_day_hint),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = { text -> viewModel.update { it.copy(note = text) } },
                label = { Text(stringResource(R.string.transaction_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle(stringResource(R.string.transaction_account))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.accountType == null,
                    onClick = { viewModel.update { it.copy(accountType = null) } },
                    label = { Text(stringResource(R.string.account_none)) },
                )
                AccountType.entries.forEach { account ->
                    FilterChip(
                        selected = state.accountType == account,
                        onClick = { viewModel.update { it.copy(accountType = account) } },
                        label = { Text(stringResource(accountLabel(account))) },
                    )
                }
            }
            SwitchRow(
                checked = state.active,
                onCheckedChange = { v -> viewModel.update { it.copy(active = v) } },
                label = stringResource(R.string.recurring_active),
                description = stringResource(R.string.recurring_active_hint),
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.recurring_delete_title),
            message = stringResource(R.string.recurring_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
