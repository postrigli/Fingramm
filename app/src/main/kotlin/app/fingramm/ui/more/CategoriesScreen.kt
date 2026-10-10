@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.fingramm.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.model.CategoryKind
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.money.Money
import app.fingramm.ui.common.BackTopBar
import app.fingramm.ui.common.EmptyState
import app.fingramm.ui.common.LoadingBox
import app.fingramm.ui.common.MoneyField
import app.fingramm.ui.common.PrimaryButton
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.SwitchRow

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onAdd: (TransactionType) -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val type = if (tab == 0) TransactionType.EXPENSE else TransactionType.INCOME
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.categories_title), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(type) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.category_add))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.expenses)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.income)) })
            }
            val list = state.categories.filter { it.type == type }
            if (list.isEmpty()) {
                EmptyState(stringResource(R.string.categories_empty), stringResource(R.string.categories_empty_hint))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { category ->
                        val parts = buildList {
                            if (type == TransactionType.EXPENSE) {
                                add(
                                    stringResource(
                                        when {
                                            category.isDebtPayment -> R.string.category_kind_debt
                                            category.kind == CategoryKind.MANDATORY -> R.string.category_kind_mandatory
                                            else -> R.string.category_kind_variable
                                        },
                                    ),
                                )
                                state.limits[category.id]?.let {
                                    add(stringResource(R.string.category_limit_value, Money.format(it, state.currency)))
                                }
                            }
                            if (category.archived) add(stringResource(R.string.category_archived))
                        }
                        ListItem(
                            leadingContent = {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Text(category.icon) }
                            },
                            headlineContent = { Text(category.name) },
                            supportingContent = { Text(parts.joinToString(" · ")) },
                            modifier = Modifier.clickable { onEdit(category.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryEditScreen(onBack: () -> Unit, viewModel: CategoryEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onBack() } }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            BackTopBar(
                stringResource(if (state.editingId == null) R.string.category_add else R.string.category_edit),
                onBack,
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
        val isExpense = state.type == TransactionType.EXPENSE
        ScreenColumn(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { text -> viewModel.update { it.copy(name = text, duplicateName = false) } },
                label = { Text(stringResource(R.string.category_name)) },
                isError = state.nameError || state.duplicateName,
                supportingText = when {
                    state.duplicateName -> {
                        { Text(stringResource(R.string.category_duplicate)) }
                    }
                    state.nameError -> {
                        { Text(stringResource(R.string.error_required)) }
                    }
                    else -> null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.editingId == null) {
                val types =
                    listOf(TransactionType.EXPENSE to R.string.expenses, TransactionType.INCOME to R.string.income)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = state.type == value,
                            onClick = { viewModel.update { it.copy(type = value) } },
                            shape = SegmentedButtonDefaults.itemShape(index, types.size),
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
            SectionTitle(stringResource(R.string.category_icon))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryEmojis.forEach { emoji ->
                    FilterChip(
                        selected = state.icon == emoji,
                        onClick = { viewModel.update { it.copy(icon = emoji) } },
                        label = { Text(emoji) },
                    )
                }
            }
            if (isExpense) {
                SectionTitle(stringResource(R.string.category_kind))
                val kinds = listOf(
                    CategoryKind.MANDATORY to R.string.category_kind_mandatory,
                    CategoryKind.VARIABLE to R.string.category_kind_variable,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    kinds.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = state.kind == value,
                            onClick = { viewModel.update { it.copy(kind = value) } },
                            shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                Text(stringResource(R.string.category_kind_hint), style = MaterialTheme.typography.bodySmall)
                if (state.kind == CategoryKind.MANDATORY) {
                    SwitchRow(
                        checked = state.isDebtPayment,
                        onCheckedChange = { v -> viewModel.update { it.copy(isDebtPayment = v) } },
                        label = stringResource(R.string.category_is_debt),
                        description = stringResource(R.string.category_is_debt_hint),
                    )
                }
                MoneyField(
                    valueMinor = state.limitMinor,
                    onValueChange = { v -> viewModel.update { it.copy(limitMinor = v) } },
                    label = stringResource(R.string.category_limit),
                    supportingText = stringResource(R.string.category_limit_hint),
                )
            }
            if (state.editingId != null) {
                SwitchRow(
                    checked = state.archived,
                    onCheckedChange = { v -> viewModel.update { it.copy(archived = v) } },
                    label = stringResource(R.string.category_archive),
                    description = stringResource(R.string.category_archive_hint),
                )
            }
        }
    }
}
