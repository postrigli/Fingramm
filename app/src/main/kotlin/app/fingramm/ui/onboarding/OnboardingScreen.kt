@file:OptIn(ExperimentalMaterial3Api::class)

package app.fingramm.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.ui.common.BalanceInput
import app.fingramm.ui.common.IntField
import app.fingramm.ui.common.MoneyField
import app.fingramm.ui.common.NotificationPermissionRequest
import app.fingramm.ui.common.PrimaryButton
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.SwitchRow
import app.fingramm.ui.common.TimePickerDialogHost
import app.fingramm.ui.common.formatTime
import app.fingramm.ui.common.rememberNotificationPermissionRequest
import app.fingramm.ui.more.MonthStartDialog

private const val MINUTES_PER_HOUR = 60

/** Первый запуск: один экран с настройками, которые упрощают ежедневное внесение операций. */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onRestore: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.finished.collect { onFinished() } }
    OnboardingContent(
        state = state,
        onUpdate = viewModel::update,
        onStart = viewModel::start,
        onRestore = onRestore,
        requestPermission = rememberNotificationPermissionRequest(),
    )
}

/** Без состояния и без Hilt: экран тестируется отдельно. */
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onUpdate: ((OnboardingUiState) -> OnboardingUiState) -> Unit,
    onStart: () -> Unit,
    onRestore: () -> Unit,
    requestPermission: NotificationPermissionRequest,
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                PrimaryButton(
                    text = stringResource(R.string.action_start),
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    onClick = {
                        // Разрешение просим в момент старта, когда понятно, что именно будет приходить; отказ
                        // не блокирует: приложение работает без уведомлений.
                        if (state.valid && state.needsNotifications) requestPermission { onStart() } else onStart()
                    },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.onboarding_subtitle), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.survey_welcome_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CurrencySection(state, onUpdate)
            BalanceSection(state, onUpdate)
            MonthStartSection(state, onUpdate)
            IncomeSection(state, onUpdate)
            RecurringSection(state, onUpdate)
            EveningSection(state, onUpdate)
            TextButton(onClick = onRestore) { Text(stringResource(R.string.onboarding_restore)) }
        }
    }
}

@Composable
private fun CurrencySection(state: OnboardingUiState, onUpdate: ((OnboardingUiState) -> OnboardingUiState) -> Unit) {
    SectionTitle(stringResource(R.string.survey_currency_title))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CurrencyCode.entries.forEach { currency ->
            FilterChip(
                selected = state.currency == currency,
                onClick = { onUpdate { it.copy(currency = currency) } },
                label = { Text("${currency.code} ${currency.symbol}") },
            )
        }
    }
}

@Composable
private fun BalanceSection(state: OnboardingUiState, onUpdate: ((OnboardingUiState) -> OnboardingUiState) -> Unit) {
    SectionTitle(stringResource(R.string.onboarding_balance_title))
    Text(stringResource(R.string.onboarding_balance_hint), style = MaterialTheme.typography.bodySmall)
    BalanceInput(
        valueMinor = state.balanceMinor,
        onValueChange = { value -> onUpdate { it.copy(balanceMinor = value) } },
        suffix = state.currency.symbol,
    )
}

@Composable
private fun MonthStartSection(state: OnboardingUiState, onUpdate: ((OnboardingUiState) -> OnboardingUiState) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.onboarding_month_title))
    Text(stringResource(R.string.onboarding_month_hint), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_month_button, state.monthStartDay))
    }
    if (picking) {
        MonthStartDialog(
            selected = state.monthStartDay,
            onSelect = { day ->
                onUpdate { it.copy(monthStartDay = day) }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun RecurringSection(state: OnboardingUiState, onUpdate: ((OnboardingUiState) -> OnboardingUiState) -> Unit) {
    var pickingTime by remember { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.onboarding_recurring_title))
    Text(stringResource(R.string.onboarding_recurring_hint), style = MaterialTheme.typography.bodySmall)
    state.rows.forEachIndexed { index, row ->
        StarterRowItem(
            row = row,
            showErrors = state.showErrors,
            onChange = { changed ->
                onUpdate { s -> s.copy(rows = s.rows.toMutableList().also { it[index] = changed }) }
            },
        )
    }
    if (state.anyRecurring) {
        val time = formatTime(state.notifyMinute / MINUTES_PER_HOUR, state.notifyMinute % MINUTES_PER_HOUR)
        OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.recurring_notify_time, time))
        }
    }
    if (pickingTime) {
        TimePickerDialogHost(
            hour = state.notifyMinute / MINUTES_PER_HOUR,
            minute = state.notifyMinute % MINUTES_PER_HOUR,
            onDismiss = { pickingTime = false },
            onConfirm = { hour, minute ->
                onUpdate { it.copy(notifyMinute = hour * MINUTES_PER_HOUR + minute) }
                pickingTime = false
            },
        )
    }
}

@Composable
private fun StarterRowItem(row: StarterRow, showErrors: Boolean, onChange: (StarterRow) -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = row.enabled,
                    role = Role.Checkbox,
                    onValueChange = { onChange(row.copy(enabled = it)) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = row.enabled, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Text(stringResource(row.payment.titleRes), style = MaterialTheme.typography.bodyLarge)
        }
        if (row.enabled) {
            Row(
                Modifier.padding(start = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MoneyField(
                    valueMinor = row.amountMinor,
                    onValueChange = { onChange(row.copy(amountMinor = it)) },
                    label = stringResource(R.string.transaction_amount),
                    modifier = Modifier.weight(1f),
                    isError = showErrors && !row.amountValid,
                )
                IntField(
                    value = row.day,
                    onValueChange = { onChange(row.copy(day = it)) },
                    label = stringResource(R.string.onboarding_day),
                    modifier = Modifier.weight(1f),
                    isError = showErrors && !row.dayValid,
                )
            }
        }
    }
}

@Composable
private fun EveningSection(state: OnboardingUiState, onUpdate: ((OnboardingUiState) -> OnboardingUiState) -> Unit) {
    var pickingTime by remember { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.onboarding_evening_title))
    SwitchRow(
        checked = state.eveningReminder,
        onCheckedChange = { checked -> onUpdate { it.copy(eveningReminder = checked) } },
        label = stringResource(R.string.onboarding_evening_label),
        description = stringResource(R.string.onboarding_evening_hint),
    )
    if (state.eveningReminder) {
        val time = formatTime(state.eveningMinute / MINUTES_PER_HOUR, state.eveningMinute % MINUTES_PER_HOUR)
        OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_evening_time, time))
        }
    }
    if (pickingTime) {
        TimePickerDialogHost(
            hour = state.eveningMinute / MINUTES_PER_HOUR,
            minute = state.eveningMinute % MINUTES_PER_HOUR,
            onDismiss = { pickingTime = false },
            onConfirm = { hour, minute ->
                onUpdate { it.copy(eveningMinute = hour * MINUTES_PER_HOUR + minute) }
                pickingTime = false
            },
        )
    }
}
