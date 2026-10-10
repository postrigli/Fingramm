@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.fingramm.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.Transaction
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.money.Money
import app.fingramm.domain.recurring.PendingOccurrence
import app.fingramm.domain.stats.LimitStatus
import app.fingramm.ui.common.EmptyState
import app.fingramm.ui.common.MAX_CONTENT_WIDTH
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.formatDate
import app.fingramm.ui.common.formatMonth
import app.fingramm.ui.common.formatWeekday
import app.fingramm.ui.theme.LocalStatusColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun TransactionsScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenRecurring: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    val deletedMessage = stringResource(R.string.transaction_deleted)
    LaunchedEffect(Unit) {
        viewModel.deleted.collect { removed ->
            val result = snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(removed)
        }
    }
    var showPending by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_transactions)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.transaction_add))
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "overview") { state.overview?.let { OverviewCard(state, it, viewModel) } }
                if (state.pending.isNotEmpty()) {
                    item(key = "pending") {
                        Card(Modifier.fillMaxWidth().clickable { showPending = true }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.pending_banner, state.pending.size),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(stringResource(R.string.pending_review), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item(key = "filters") { FiltersBlock(state, viewModel, onOpenRecurring) }

                if (state.groups.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        if (state.isFiltered) {
                            EmptyState(
                                stringResource(R.string.transactions_nothing_found),
                                stringResource(R.string.transactions_nothing_found_hint),
                            )
                        } else {
                            EmptyState(
                                stringResource(R.string.transactions_empty_title),
                                stringResource(R.string.transactions_empty_message),
                                action = { Button(onClick = onAdd) { Text(stringResource(R.string.transaction_add)) } },
                            )
                        }
                    }
                }
                state.groups.forEach { group ->
                    item(key = "day-${group.date}") { DayHeader(group, state) }
                    items(group.items, key = { it.id }) { tx ->
                        TransactionRow(
                            tx = tx,
                            category = state.categoriesById[tx.categoryId],
                            currency = state.currency,
                            onClick = { onEdit(tx.id) },
                            onDelete = { viewModel.delete(tx) },
                        )
                    }
                }
                if (state.hasMore) {
                    item(key = "more") {
                        LaunchedEffect(state.filters.limit) { viewModel.loadMore() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showPending && state.pending.isNotEmpty()) {
        PendingSheet(
            pending = state.pending,
            state = state,
            onConfirm = viewModel::confirmPending,
            onSkip = viewModel::skipPending,
            onDismiss = { showPending = false },
        )
    }
}

@Composable
private fun OverviewCard(state: TransactionsUiState, overview: MonthOverview, viewModel: TransactionsViewModel) {
    val money = { minor: Long -> Money.format(minor, state.currency) }
    val colors = LocalStatusColors.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = viewModel::previousOverviewMonth) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.month_previous),
                    )
                }
                Text(
                    "${stringResource(R.string.overview_title)} · ${formatMonth(overview.month)}",
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::nextOverviewMonth, enabled = overview.month < state.currentMonth) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.month_next),
                    )
                }
            }
            OverviewLine(stringResource(R.string.income), "+${money(overview.totals.incomeMinor)}", colors.income)
            OverviewLine(stringResource(R.string.expenses), "−${money(overview.totals.expensesMinor)}", colors.expense)
            HorizontalDivider()
            val balance = overview.totals.balanceMinor
            OverviewLine(
                stringResource(R.string.overview_balance),
                (if (balance > 0) "+" else "") + money(balance),
                if (balance >= 0) colors.income else colors.expense,
                bold = true,
            )
            Text(
                stringResource(
                    R.string.overview_vs_plan,
                    money(overview.planIncomeMinor),
                    money(overview.planExpensesMinor),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (overview.limits.isNotEmpty()) {
                SectionTitle(stringResource(R.string.overview_limits))
                overview.limits.forEach { row -> LimitLine(row, money) }
            }
        }
    }
}

@Composable
private fun OverviewLine(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            color = color,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun LimitLine(row: LimitRow, money: (Long) -> String) {
    val colors = LocalStatusColors.current
    val statusText = when (row.usage.status) {
        LimitStatus.OK -> stringResource(R.string.limit_ok)
        LimitStatus.ATTENTION -> stringResource(R.string.limit_attention)
        LimitStatus.EXCEEDED -> stringResource(R.string.limit_exceeded)
    }
    val color = when (row.usage.status) {
        LimitStatus.OK -> colors.good
        LimitStatus.ATTENTION -> colors.attention
        LimitStatus.EXCEEDED -> colors.risk
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${row.category.icon} ${row.category.name}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${money(row.usage.spentMinor)} / ${money(row.usage.limit.limitMinor)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LinearProgressIndicator(
            progress = { row.usage.ratio.coerceIn(0.0, 1.0).toFloat() },
            color = color,
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Text(statusText, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun FiltersBlock(state: TransactionsUiState, viewModel: TransactionsViewModel, onOpenRecurring: () -> Unit) {
    val filters = state.filters
    var pickingCategory by remember { mutableStateOf(false) }
    var pickingRange by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = filters.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PeriodPreset.THIS_MONTH to R.string.period_this_month,
                PeriodPreset.LAST_MONTH to R.string.period_last_month,
                PeriodPreset.ALL to R.string.period_all,
            ).forEach { (preset, label) ->
                FilterChip(
                    selected = filters.period == preset,
                    onClick = { viewModel.setPeriod(preset) },
                    label = { Text(stringResource(label)) },
                )
            }
            FilterChip(
                selected = filters.period == PeriodPreset.CUSTOM,
                onClick = { pickingRange = true },
                label = { Text(stringResource(R.string.period_custom)) },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filters.type == null,
                onClick = { viewModel.setType(null) },
                label = { Text(stringResource(R.string.filter_all)) },
            )
            FilterChip(
                selected = filters.type == TransactionType.INCOME,
                onClick = { viewModel.setType(TransactionType.INCOME) },
                label = { Text(stringResource(R.string.income)) },
            )
            FilterChip(
                selected = filters.type == TransactionType.EXPENSE,
                onClick = { viewModel.setType(TransactionType.EXPENSE) },
                label = { Text(stringResource(R.string.expenses)) },
            )
            val selectedCategory = state.categoriesById[filters.categoryId]
            FilterChip(
                selected = selectedCategory != null,
                onClick = { pickingCategory = true },
                label = {
                    Text(
                        selectedCategory?.let { "${it.icon} ${it.name}" } ?: stringResource(R.string.filter_category),
                    )
                },
            )
        }
        TextButton(onClick = onOpenRecurring) { Text(stringResource(R.string.recurring_title)) }
    }
    if (pickingCategory) {
        CategoryFilterDialog(
            categories = state.categories,
            selectedId = filters.categoryId,
            onSelect = {
                viewModel.setCategory(it)
                pickingCategory = false
            },
            onDismiss = { pickingCategory = false },
        )
    }
    if (pickingRange) {
        RangeDialog(
            onDismiss = { pickingRange = false },
            onConfirm = { from, to ->
                viewModel.setCustomPeriod(from, to)
                pickingRange = false
            },
        )
    }
}

@Composable
private fun CategoryFilterDialog(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_category)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.filter_all_categories)) }
                categories.forEach { category ->
                    TextButton(onClick = { onSelect(category.id) }) {
                        Text(
                            "${category.icon} ${category.name}" + if (category.id == selectedId) " ✓" else "",
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun RangeDialog(onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis
                    val end = state.selectedEndDateMillis
                    if (start != null && end != null) onConfirm(start.toLocalDate(), end.toLocalDate())
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(500.dp))
    }
}

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun DayHeader(group: DayGroup, state: TransactionsUiState) {
    val money = Money.format(group.netMinor, state.currency)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "${formatDate(group.date, state.today)}, ${formatWeekday(group.date)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            (if (group.netMinor > 0) "+" else "") + money,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransactionRow(
    tx: Transaction,
    category: Category?,
    currency: CurrencyCode,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalStatusColors.current
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    val isIncome = tx.type == TransactionType.INCOME
    val amount = (if (isIncome) "+" else "−") + Money.format(tx.amountMinor, currency)
    val typeLabel = stringResource(if (isIncome) R.string.income else R.string.expense)
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$typeLabel $amount, ${category?.name.orEmpty()}, ${tx.note}"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(category?.icon ?: "•", style = MaterialTheme.typography.titleMedium) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    category?.name ?: stringResource(R.string.category_unknown),
                    style = MaterialTheme.typography.bodyLarge,
                )
                val subtitle = listOfNotNull(
                    tx.note.takeIf {
                        it.isNotBlank()
                    },
                    tx.accountType?.let { stringResource(accountLabel(it)) },
                )
                    .joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                amount,
                style = MaterialTheme.typography.titleSmall,
                color = if (isIncome) colors.income else colors.expense,
            )
        }
    }
}

@Composable
private fun PendingSheet(
    pending: List<PendingOccurrence>,
    state: TransactionsUiState,
    onConfirm: (PendingOccurrence) -> Unit,
    onSkip: (PendingOccurrence) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.pending_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.pending_hint), style = MaterialTheme.typography.bodyMedium)
            // Списания идут по порядку: пока не обработано ближайшее по шаблону, следующие недоступны.
            val earliest = pending.groupBy { it.template.id }.mapValues { (_, list) -> list.minOf { it.dueDate } }
            pending.forEach { item ->
                val template = item.template
                val actionable = item.dueDate == earliest[template.id]
                val category = state.categoriesById[template.categoryId]
                val sign = if (template.type == TransactionType.INCOME) "+" else "−"
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${category?.icon.orEmpty()} ${category?.name.orEmpty()} · $sign${Money.format(
                                template.amountMinor,
                                state.currency,
                            )}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            listOf(
                                formatDate(item.dueDate, state.today),
                                template.note,
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onConfirm(item) }, enabled = actionable) {
                                Text(stringResource(R.string.pending_confirm))
                            }
                            OutlinedButton(onClick = { onSkip(item) }, enabled = actionable) {
                                Text(stringResource(R.string.pending_skip))
                            }
                        }
                    }
                }
            }
        }
    }
}
