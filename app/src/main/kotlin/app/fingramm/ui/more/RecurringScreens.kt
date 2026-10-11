@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.fingramm.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.model.AccountType
import app.fingramm.domain.model.RecurrenceFrequency
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.money.Money
import app.fingramm.ui.common.BackTopBar
import app.fingramm.ui.common.ConfirmDialog
import app.fingramm.ui.common.EmptyState
import app.fingramm.ui.common.LoadingBox
import app.fingramm.ui.common.LocalCurrency
import app.fingramm.ui.common.MoneyField
import app.fingramm.ui.common.PrimaryButton
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.SwitchRow
import app.fingramm.ui.common.TimePickerDialogHost
import app.fingramm.ui.common.formatTime
import app.fingramm.ui.common.notificationsAllowed
import app.fingramm.ui.common.rememberNotificationPermissionRequest
import app.fingramm.ui.common.weekdayName
import app.fingramm.ui.common.weekdayShort
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
                                listOf(recurrenceSummary(template), status, template.note)
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
    var pickingDay by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val requestPermission = rememberNotificationPermissionRequest()
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
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
                onClick = {
                    // Уведомления включены, а разрешения нет: спросим при сохранении (отказ не мешает сохранить).
                    if (state.notifyEnabled && !allowed) {
                        requestPermission { granted ->
                            allowed = granted
                            viewModel.save()
                        }
                    } else {
                        viewModel.save()
                    }
                },
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
            ScheduleSection(
                state = state,
                onChange = viewModel::update,
                allowed = allowed,
                onNotifyChosen = { enabled -> if (enabled) requestPermission { granted -> allowed = granted } },
                onPickDay = { pickingDay = true },
                onPickTime = { pickingTime = true },
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
    if (pickingDay) {
        DayOfMonthDialog(
            selected = state.dayOfMonth,
            onSelect = { day ->
                viewModel.update { it.copy(dayOfMonth = day) }
                pickingDay = false
            },
            onDismiss = { pickingDay = false },
        )
    }
    if (pickingTime) {
        TimePickerDialogHost(
            hour = state.notifyAtMinute / MINUTES_IN_HOUR,
            minute = state.notifyAtMinute % MINUTES_IN_HOUR,
            onDismiss = { pickingTime = false },
            onConfirm = { hour, minute ->
                viewModel.update { it.copy(notifyAtMinute = hour * MINUTES_IN_HOUR + minute) }
                pickingTime = false
            },
        )
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

/** Повтор (ежемесячно или по дням недели) и время уведомления под ним. */
@Composable
private fun ColumnScope.ScheduleSection(
    state: RecurringFormState,
    onChange: ((RecurringFormState) -> RecurringFormState) -> Unit,
    allowed: Boolean,
    onNotifyChosen: (Boolean) -> Unit,
    onPickDay: () -> Unit,
    onPickTime: () -> Unit,
) {
    SectionTitle(stringResource(R.string.recurring_repeat))
    val frequencies = listOf(
        RecurrenceFrequency.MONTHLY to R.string.recurring_monthly,
        RecurrenceFrequency.WEEKLY to R.string.recurring_weekly,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        frequencies.forEachIndexed { index, (frequency, label) ->
            SegmentedButton(
                selected = state.frequency == frequency,
                onClick = { onChange { it.copy(frequency = frequency) } },
                shape = SegmentedButtonDefaults.itemShape(index, frequencies.size),
                label = { Text(stringResource(label)) },
            )
        }
    }
    when (state.frequency) {
        RecurrenceFrequency.MONTHLY -> {
            OutlinedButton(onClick = onPickDay, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.recurring_monthly_button, state.dayOfMonth))
            }
            Text(
                stringResource(R.string.recurring_day_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RecurrenceFrequency.WEEKLY -> {
            WeekdayToggles(
                selected = state.weekdays,
                onToggle = { day ->
                    onChange {
                        it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day)
                    }
                },
            )
            if (state.weekdaysError) {
                Text(
                    stringResource(R.string.recurring_weekdays_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    SwitchRow(
        checked = state.notifyEnabled,
        onCheckedChange = { enabled ->
            onChange { it.copy(notifyEnabled = enabled) }
            onNotifyChosen(enabled)
        },
        label = stringResource(R.string.recurring_notify),
        description = stringResource(R.string.recurring_notify_hint),
    )
    if (state.notifyEnabled) {
        OutlinedButton(onClick = onPickTime, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    R.string.recurring_notify_time,
                    formatTime(state.notifyAtMinute / MINUTES_IN_HOUR, state.notifyAtMinute % MINUTES_IN_HOUR),
                ),
            )
        }
        if (!allowed) {
            Text(
                stringResource(R.string.recurring_notify_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Подпись расписания для списка: «каждый месяц, 15-го · уведомление в 09:00» или «по пн, ср, пт · …». */
@Composable
private fun recurrenceSummary(template: RecurringTemplate): String {
    val schedule = when (template.frequency) {
        RecurrenceFrequency.MONTHLY -> stringResource(R.string.recurring_every_month, template.dayOfMonth)
        RecurrenceFrequency.WEEKLY -> stringResource(
            R.string.recurring_every_weekdays,
            template.weekdays.sorted().joinToString(", ") { weekdayShort(it).lowercase() },
        )
    }
    val notify = template.notifyAtMinute?.let {
        stringResource(R.string.recurring_notify_at, formatTime(it / MINUTES_IN_HOUR, it % MINUTES_IN_HOUR))
    }
    return listOfNotNull(schedule, notify).joinToString(" · ")
}

/** Дни недели как у будильника: семь кнопок ПН–ВС, каждую можно включить или выключить. */
@Composable
private fun WeekdayToggles(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (day in 1..WEEK_DAYS) {
            val name = weekdayName(day)
            FilledIconToggleButton(
                checked = day in selected,
                onCheckedChange = { onToggle(day) },
                modifier = Modifier.size(WEEKDAY_BUTTON_DP.dp).semantics { contentDescription = name },
            ) { Text(weekdayShort(day), style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** Выбор дня месяца в виде календарной сетки 1–31. Выбор сразу применяется. */
@Composable
private fun DayOfMonthDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurring_day_dialog_title)) },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(DAYS_PER_ROW),
                    modifier = Modifier.heightIn(max = DAY_GRID_MAX_HEIGHT_DP.dp),
                ) {
                    items((1..RecurringFormState.MAX_DAY).toList()) { day ->
                        val chosen = day == selected
                        val scheme = MaterialTheme.colorScheme
                        Surface(
                            onClick = { onSelect(day) },
                            shape = CircleShape,
                            color = if (chosen) scheme.primary else Color.Transparent,
                            contentColor = if (chosen) scheme.onPrimary else scheme.onSurface,
                            modifier = Modifier.padding(2.dp).size(DAY_CELL_DP.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(day.toString()) }
                        }
                    }
                }
                Text(
                    stringResource(R.string.recurring_day_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private const val WEEK_DAYS = 7
private const val MINUTES_IN_HOUR = 60
private const val WEEKDAY_BUTTON_DP = 40
private const val DAYS_PER_ROW = 7
private const val DAY_CELL_DP = 40
private const val DAY_GRID_MAX_HEIGHT_DP = 260
