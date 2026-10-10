@file:OptIn(ExperimentalMaterial3Api::class)

package app.fingramm.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.model.GoalHorizon
import app.fingramm.domain.money.Money
import app.fingramm.ui.common.BackTopBar
import app.fingramm.ui.common.ConfirmDialog
import app.fingramm.ui.common.EmptyState
import app.fingramm.ui.common.LoadingBox
import app.fingramm.ui.common.LocalCurrency
import app.fingramm.ui.common.MAX_CONTENT_WIDTH
import app.fingramm.ui.common.MoneyField
import app.fingramm.ui.common.PrimaryButton
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.formatDate
import app.fingramm.ui.survey.DateField

@Composable
fun GoalsScreen(onAdd: () -> Unit, onEdit: (Long) -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var topUp by remember { mutableStateOf<GoalItem?>(null) }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_goals)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.goal_add))
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.isEmpty -> EmptyState(
                title = stringResource(R.string.goals_empty_title),
                message = stringResource(R.string.goals_empty_message),
                modifier = Modifier.padding(padding),
                action = { Button(onClick = onAdd) { Text(stringResource(R.string.goal_add)) } },
            )
            else -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier
                        .widthIn(max = MAX_CONTENT_WIDTH)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            stringResource(
                                R.string.goals_flow_note,
                                Money.format(state.monthlyFlowMinor, state.currency),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.year.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.goals_year)) }
                        items(
                            state.year,
                            key = { it.goal.id },
                        ) { GoalCard(it, state, onEdit = onEdit, onTopUp = { topUp = it }) }
                    }
                    if (state.mid.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.goals_mid)) }
                        items(
                            state.mid,
                            key = { it.goal.id },
                        ) { GoalCard(it, state, onEdit = onEdit, onTopUp = { topUp = it }) }
                    }
                }
            }
        }
    }
    topUp?.let { item ->
        TopUpDialog(
            title = item.goal.title,
            onDismiss = { topUp = null },
            onConfirm = {
                viewModel.addToSaved(item.goal.id, it)
                topUp = null
            },
        )
    }
}

@Composable
private fun GoalCard(item: GoalItem, state: GoalsUiState, onEdit: (Long) -> Unit, onTopUp: () -> Unit) {
    val goal = item.goal
    val f = item.forecast
    val money = { minor: Long -> Money.format(minor, state.currency) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(goal.title, style = MaterialTheme.typography.titleMedium)
            val progress = f.progress
            if (progress != null) {
                val percent = Money.formatPercent(progress)
                LinearProgressIndicator(
                    progress = { progress.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .semantics { contentDescription = "${goal.title}: $percent" },
                )
                Text(
                    stringResource(
                        R.string.goal_saved_of_percent,
                        money(goal.savedMinor),
                        money(goal.targetMinor ?: 0),
                        percent,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    stringResource(R.string.goal_saved_only, money(goal.savedMinor)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            goal.deadline?.let {
                Text(
                    stringResource(R.string.goal_deadline, formatDate(it, state.today)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ForecastLines(item, state, money)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTopUp) { Text(stringResource(R.string.goal_top_up)) }
                OutlinedButton(onClick = { onEdit(goal.id) }) { Text(stringResource(R.string.action_edit)) }
            }
        }
    }
}

@Composable
private fun ForecastLines(item: GoalItem, state: GoalsUiState, money: (Long) -> String) {
    val f = item.forecast
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        f.progress == null -> Unit
        f.reached -> Text(stringResource(R.string.goal_reached), style = MaterialTheme.typography.titleSmall)
        else -> {
            if (f.deadlinePassed) {
                Text(
                    stringResource(R.string.goal_deadline_passed),
                    style = style,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            f.requiredPerMonthMinor?.let {
                val verdict = if (f.onTrack == true) R.string.goal_on_track else R.string.goal_off_track
                Text(
                    stringResource(R.string.goal_required_per_month, money(it)) + " " + stringResource(verdict),
                    style = style,
                    color = color,
                )
            }
            val months = f.monthsAtCurrentFlow
            val date = f.projectedDate
            if (months != null && date != null) {
                Text(
                    stringResource(
                        R.string.goal_forecast,
                        money(state.monthlyFlowMinor),
                        months,
                        formatDate(date, state.today),
                    ),
                    style = style,
                    color = color,
                )
            } else {
                Text(stringResource(R.string.goal_forecast_unreachable), style = style, color = color)
            }
        }
    }
}

@Composable
private fun TopUpDialog(title: String, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var amount by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_top_up_title, title)) },
        text = {
            MoneyField(
                valueMinor = amount,
                onValueChange = { amount = it },
                label = stringResource(R.string.transaction_amount),
                suffix = LocalCurrency.current.symbol,
            )
        },
        confirmButton = {
            TextButton(enabled = (amount ?: 0) > 0, onClick = { amount?.let(onConfirm) }) {
                Text(stringResource(R.string.goal_top_up))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun GoalEditScreen(onBack: () -> Unit, viewModel: GoalEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onBack() } }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            BackTopBar(
                title = stringResource(if (state.editingId == null) R.string.goal_add else R.string.goal_edit),
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
            OutlinedTextField(
                value = state.title,
                onValueChange = { text -> viewModel.update { it.copy(title = text) } },
                label = { Text(stringResource(R.string.survey_goal_title)) },
                isError = state.titleError,
                supportingText = if (state.titleError) {
                    { Text(stringResource(R.string.error_required)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyField(
                valueMinor = state.targetMinor,
                onValueChange = { v -> viewModel.update { it.copy(targetMinor = v) } },
                label = stringResource(R.string.survey_goal_target),
                isError = state.targetError,
                supportingText = if (state.targetError) {
                    stringResource(
                        R.string.error_must_be_positive,
                    )
                } else {
                    stringResource(R.string.survey_optional)
                },
                suffix = LocalCurrency.current.symbol,
            )
            MoneyField(
                valueMinor = state.savedMinor,
                onValueChange = { v -> viewModel.update { it.copy(savedMinor = v) } },
                label = stringResource(R.string.goal_saved_label),
                suffix = LocalCurrency.current.symbol,
            )
            DateField(
                label = stringResource(R.string.survey_goal_deadline),
                date = state.deadline,
                onDateChange = { d -> viewModel.update { it.copy(deadline = d) } },
            )
            SectionTitle(stringResource(R.string.goal_horizon))
            val options = listOf(GoalHorizon.YEAR to R.string.goals_year, GoalHorizon.MID to R.string.goals_mid)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = state.horizon == value,
                        onClick = { viewModel.update { it.copy(horizon = value) } },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.goal_delete_title),
            message = stringResource(R.string.goal_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
