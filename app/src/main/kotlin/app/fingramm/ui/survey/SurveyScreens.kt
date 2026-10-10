@file:OptIn(ExperimentalMaterial3Api::class)

package app.fingramm.ui.survey

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.fingramm.data.repo.BudgetData
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.money.Money
import app.fingramm.domain.survey.SurveyStep
import app.fingramm.ui.common.BackTopBar
import app.fingramm.ui.common.LoadingBox
import app.fingramm.ui.common.PrimaryButton
import app.fingramm.ui.common.RadioOption
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle

/** Шаги, доступные для редактирования из сводки (цели редактируются на вкладке «Цели»). */
private val EditableSteps = listOf(
    SurveyStep.FAMILY,
    SurveyStep.INCOME,
    SurveyStep.MANDATORY,
    SurveyStep.VARIABLE,
    SurveyStep.DEBTS,
    SurveyStep.CUSHION,
    SurveyStep.ASSETS,
    SurveyStep.HABITS,
)

@Composable
fun SurveySummaryScreen(
    onBack: () -> Unit,
    onOpenStep: (SurveyStep) -> Unit,
    viewModel: SurveySummaryViewModel = hiltViewModel(),
) {
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    var changingCurrency by remember { mutableStateOf(false) }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.survey_summary_title), onBack) }) { padding ->
        val data = budget
        if (data == null) {
            LoadingBox(Modifier.padding(padding))
        } else {
            ScreenColumn(Modifier.padding(padding)) {
                Text(stringResource(R.string.survey_summary_hint), style = MaterialTheme.typography.bodyMedium)
                Column {
                    EditableSteps.forEach { step ->
                        ListItem(
                            headlineContent = { Text(stringResource(stepTitle(step))) },
                            supportingContent = { Text(summaryText(step, data)) },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable { onOpenStep(step) },
                        )
                        HorizontalDivider()
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.survey_currency_title)) },
                        supportingContent = { Text("${data.profile.currency.symbol} ${data.profile.currency.code}") },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable { changingCurrency = true },
                    )
                }
                SectionTitle(stringResource(R.string.survey_goals_note_title))
                Text(stringResource(R.string.survey_goals_note), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (changingCurrency) {
        CurrencyDialog(
            current = budget?.profile?.currency ?: CurrencyCode.RUB,
            onDismiss = { changingCurrency = false },
            onConfirm = {
                viewModel.setCurrency(it)
                changingCurrency = false
            },
        )
    }
}

@Composable
private fun CurrencyDialog(current: CurrencyCode, onDismiss: () -> Unit, onConfirm: (CurrencyCode) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.survey_currency_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.survey_currency_change_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
                CurrencyCode.entries.forEach {
                    RadioOption(
                        selected = selected == it,
                        onSelect = { selected = it },
                        label = "${it.symbol} ${it.code}",
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun summaryText(step: SurveyStep, data: BudgetData): String {
    val currency = data.profile.currency
    fun money(minor: Long) = Money.format(minor, currency)
    val survey = data.survey
    return when (step) {
        SurveyStep.FAMILY -> stringResource(
            R.string.survey_summary_family,
            data.profile.familySize,
            data.profile.childrenCount,
        )
        SurveyStep.INCOME -> stringResource(
            R.string.survey_summary_per_month,
            money(
                data.incomeSources.sumOf {
                    it.amountMinor
                },
            ),
        )
        SurveyStep.MANDATORY -> stringResource(R.string.survey_summary_per_month, money(survey.mandatoryMonthlyMinor))
        SurveyStep.VARIABLE ->
            if (survey.variableUnknown) {
                stringResource(R.string.survey_summary_variable_unknown)
            } else {
                stringResource(R.string.survey_summary_per_month, money(survey.variableDeclaredMinor))
            }
        SurveyStep.DEBTS ->
            if (data.debts.isEmpty()) {
                stringResource(R.string.survey_summary_no_debts)
            } else {
                stringResource(R.string.survey_summary_per_month, money(data.debts.sumOf { it.monthlyPaymentMinor }))
            }
        SurveyStep.CUSHION -> stringResource(
            R.string.survey_summary_cushion,
            money(if (survey.hasCushion) survey.cushionMinor else 0L),
            money(survey.savingsTotalMinor),
        )
        SurveyStep.ASSETS -> stringResource(R.string.survey_summary_assets, data.assets.size)
        SurveyStep.HABITS -> listOfNotNull(
            survey.experience?.let { stringResource(experienceLabel(it)) },
            survey.timeCommitment?.let { stringResource(timeCommitmentLabel(it)) },
        ).joinToString(" · ").ifEmpty { stringResource(R.string.survey_summary_not_set) }
        else -> ""
    }
}

/** Редактирование одного блока ответов. */
@Composable
fun SurveyEditScreen(onBack: () -> Unit, viewModel: SurveyEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.saved.collect { onBack() } }
    Scaffold(topBar = { BackTopBar(stringResource(stepTitle(viewModel.step)), onBack) }) { padding ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(padding))
        } else {
            val errors = if (state.showErrors) state.errorsFor(viewModel.step, viewModel.today) else emptyList()
            ScreenColumn(Modifier.padding(padding)) {
                SurveyStepContent(
                    step = viewModel.step,
                    draft = state.draft,
                    errors = errors,
                    onChange = viewModel::update,
                    showReminder = false,
                )
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
