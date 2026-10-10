@file:OptIn(ExperimentalLayoutApi::class)

package app.fingramm.ui.survey

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fingramm.R
import app.fingramm.domain.model.BookkeepingExperience
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.MainGoalKind
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.SavingsPlace
import app.fingramm.domain.model.TimeCommitment
import app.fingramm.domain.survey.GoalDraft
import app.fingramm.domain.survey.ReminderDefaults
import app.fingramm.domain.survey.SavingsDraft
import app.fingramm.domain.survey.SurveyDraft
import app.fingramm.domain.survey.SurveyError
import app.fingramm.domain.survey.SurveyField
import app.fingramm.domain.survey.SurveyStep
import app.fingramm.ui.common.InfoCard
import app.fingramm.ui.common.IntField
import app.fingramm.ui.common.MoneyField
import app.fingramm.ui.common.RadioOption
import app.fingramm.ui.common.SwitchRow
import app.fingramm.ui.common.YesNoChoice
import app.fingramm.ui.common.rememberMoneyFormatter
import app.fingramm.ui.onboarding.mainGoalTitleRes

/** Изменение черновика: функция из старого состояния в новое. */
typealias DraftUpdate = ((SurveyDraft) -> SurveyDraft) -> Unit

/** Заголовок шага и пояснение «зачем мы это спрашиваем». */
@Composable
fun StepHeader(
    @StringRes title: Int,
    @StringRes why: Int?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        if (why != null) {
            InfoCard {
                Text(stringResource(R.string.survey_why_title), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(why), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@StringRes
fun stepTitle(step: SurveyStep): Int = when (step) {
    SurveyStep.WELCOME -> R.string.survey_title_welcome
    SurveyStep.FAMILY -> R.string.survey_title_family
    SurveyStep.INCOME -> R.string.survey_title_income
    SurveyStep.MANDATORY -> R.string.survey_title_mandatory
    SurveyStep.VARIABLE -> R.string.survey_title_variable
    SurveyStep.DEBTS -> R.string.survey_title_debts
    SurveyStep.CUSHION -> R.string.survey_title_cushion
    SurveyStep.ASSETS -> R.string.survey_title_assets
    SurveyStep.MAIN_GOAL -> R.string.survey_title_main_goal
    SurveyStep.MID_GOALS -> R.string.survey_title_mid_goals
    SurveyStep.HABITS -> R.string.survey_title_habits
}

/** Содержимое одного шага опроса; используется и в мастере, и в редакторе ответов. */
@Composable
fun SurveyStepContent(
    step: SurveyStep,
    draft: SurveyDraft,
    errors: List<SurveyError>,
    onChange: DraftUpdate,
    modifier: Modifier = Modifier,
    onReminderPicked: (ReminderMode) -> Unit = {},
    showReminder: Boolean = true,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (step) {
            SurveyStep.WELCOME -> WelcomeStep(draft, onChange)
            SurveyStep.FAMILY -> FamilyStep(draft, errors, onChange)
            SurveyStep.INCOME -> IncomeStep(draft, errors, onChange)
            SurveyStep.MANDATORY -> MandatoryStep(draft, errors, onChange)
            SurveyStep.VARIABLE -> VariableStep(draft, errors, onChange)
            SurveyStep.DEBTS -> DebtsStep(draft, errors, onChange)
            SurveyStep.CUSHION -> CushionStep(draft, errors, onChange)
            SurveyStep.ASSETS -> AssetsStep(draft, errors, onChange)
            SurveyStep.MAIN_GOAL -> MainGoalStep(draft, errors, onChange)
            SurveyStep.MID_GOALS -> MidGoalsStep(draft, errors, onChange)
            SurveyStep.HABITS -> HabitsStep(draft, onChange, onReminderPicked, showReminder)
        }
    }
}

@Composable
private fun WelcomeStep(draft: SurveyDraft, onChange: DraftUpdate) {
    StepHeader(R.string.survey_title_welcome, null)
    Text(stringResource(R.string.survey_welcome_body), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.survey_welcome_privacy), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.survey_currency_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.survey_currency_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CurrencyCode.entries.forEach { currency ->
            FilterChip(
                selected = draft.currency == currency,
                onClick = { onChange { it.copy(currency = currency) } },
                label = { Text("${currency.symbol} ${currency.code}") },
            )
        }
    }
    Text(
        stringResource(R.string.survey_disclaimer_short),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FamilyStep(draft: SurveyDraft, errors: List<SurveyError>, onChange: DraftUpdate) {
    StepHeader(R.string.survey_title_family, R.string.survey_why_family)
    val sizeError = errors.forField(SurveyField.FAMILY_SIZE)
    IntField(
        value = draft.familySize,
        onValueChange = { size -> onChange { it.copy(familySize = size) } },
        label = stringResource(R.string.survey_family_size),
        isError = sizeError != null,
        supportingText = sizeError?.message() ?: stringResource(R.string.survey_family_size_hint),
    )
    Text(stringResource(R.string.survey_has_children), style = MaterialTheme.typography.titleMedium)
    YesNoChoice(value = draft.hasChildren, onChange = { yes -> onChange { it.copy(hasChildren = yes) } })
    errors.forField(SurveyField.HAS_CHILDREN)?.let { ErrorLine(it) }
    if (draft.hasChildren == true) {
        val countError = errors.forField(SurveyField.CHILDREN_COUNT)
        IntField(
            value = draft.childrenCount,
            onValueChange = { count -> onChange { it.copy(childrenCount = count) } },
            label = stringResource(R.string.survey_children_count),
            isError = countError != null,
            supportingText = countError?.message(),
        )
    }
}

@Composable
private fun IncomeStep(draft: SurveyDraft, errors: List<SurveyError>, onChange: DraftUpdate) {
    val format = rememberMoneyFormatter()
    StepHeader(R.string.survey_title_income, R.string.survey_why_income)
    Text(stringResource(R.string.survey_income_hint), style = MaterialTheme.typography.bodyMedium)
    AmountRowsEditor(
        rows = draft.incomeSources,
        onRowsChange = { rows -> onChange { it.copy(incomeSources = rows) } },
        nameLabel = stringResource(R.string.survey_income_name),
        amountLabel = stringResource(R.string.survey_per_month),
        addLabel = stringResource(R.string.survey_add_income),
        errors = errors,
        field = SurveyField.INCOME,
        minRows = 1,
    )
    errors.firstOrNull { it.field == SurveyField.INCOME && it.index == null }?.let { ErrorLine(it) }
    Text(
        stringResource(R.string.survey_total_per_month, format(draft.incomeSources.sumOf { it.amountMinor ?: 0L })),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun MandatoryStep(draft: SurveyDraft, errors: List<SurveyError>, onChange: DraftUpdate) {
    StepHeader(R.string.survey_title_mandatory, R.string.survey_why_mandatory)
    Text(stringResource(R.string.survey_mandatory_hint), style = MaterialTheme.typography.bodyMedium)
    ExpenseInput(
        value = draft.mandatory,
        onChange = { value -> onChange { it.copy(mandatory = value) } },
        presetNames = stringArrayResource(R.array.survey_mandatory_items).toList(),
        allowUnknown = false,
        errors = errors,
        field = SurveyField.MANDATORY,
    )
}

@Composable
private fun VariableStep(draft: SurveyDraft, errors: List<SurveyError>, onChange: DraftUpdate) {
    StepHeader(R.string.survey_title_variable, R.string.survey_why_variable)
    Text(stringResource(R.string.survey_variable_hint), style = MaterialTheme.typography.bodyMedium)
    ExpenseInput(
        value = draft.variable,
        onChange = { value -> onChange { it.copy(variable = value) } },
        presetNames = stringArrayResource(R.array.survey_variable_items).toList(),
        allowUnknown = true,
        errors = errors,
        field = SurveyField.VARIABLE,
    )
}

@Composable
private fun DebtsStep(draft: SurveyDraft, errors: List<SurveyError>, onChange: DraftUpdate) {
    StepHeader(R.string.survey_title_debts, R.string.survey_why_debts)
    Text(stringResource(R.string.survey_has_debts), style = MaterialTheme.typography.titleMedium)
    YesNoChoice(value = draft.hasDebts, onChange = { yes -> onChange { it.copy(hasDebts = yes) } })
    errors.forField(SurveyField.HAS_DEBTS)?.let { ErrorLine(it) }
    if (draft.hasDebts == true) {
        val paymentError = errors.forField(SurveyField.DEBT_PAYMENT)
        MoneyField(
            valueMinor = draft.debtPaymentMinor,
            onValueChange = { value -> onChange { it.copy(debtPaymentMinor = value) } },
            label = stringResource(R.string.survey_debt_payment),
            supportingText = paymentError?.message() ?: stringResource(R.string.survey_debt_payment_hint),
            isError = paymentError != null,
        )
        MoneyField(
            valueMinor = draft.debtBalanceMinor,
            onValueChange = { value -> onChange { it.copy(debtBalanceMinor = value) } },
            label = stringResource(R.string.survey_debt_balance),
            supportingText = stringResource(R.string.survey_optional),
        )
    }
}

@Composable
private fun CushionStep(draft: SurveyDraft, errors: List<SurveyError>, onChange: DraftUpdate) {
    StepHeader(R.string.survey_title_cushion, R.string.survey_why_cushion)
    Text(stringResource(R.string.survey_has_cushion), style = MaterialTheme.typography.titleMedium)
    YesNoChoice(value = draft.hasCushion, onChange = { yes -> onChange { it.copy(hasCushion = yes) } })
    errors.forField(SurveyField.HAS_CUSHION)?.let { ErrorLine(it) }
    if (draft.hasCushion == true) {
        val amountError = errors.forField(SurveyField.CUSHION_AMOUNT)
        MoneyField(
            valueMinor = draft.cushionMinor,
            onValueChange = { value -> onChange { it.copy(cushionMinor = value) } },
            label = stringResource(R.string.survey_cushion_amount),
            supportingText = amountError?.message() ?: stringResource(R.string.survey_cushion_hint),
            isError = amountError != null,
        )
    }
    Text(stringResource(R.string.survey_savings_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.survey_savings_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SavingsPlace.entries.forEach { place ->
            val selected = draft.savings.any { it.place == place }
            FilterChip(
                selected = selected,
                onClick = {
                    onChange {
                        it.copy(
                            savings = if (selected) {
                                it.savings.filterNot { s -> s.place == place }
                            } else {
                                it.savings + SavingsDraft(place)
                            },
                        )
                    }
                },
                label = { Text(stringResource(savingsPlaceLabel(place))) },
            )
        }
    }
    draft.savings.forEach { entry ->
        MoneyField(
            valueMinor = entry.amountMinor,
            onValueChange = { value ->
                onChange {
                    it.copy(
                        savings = it.savings.map {
                                s ->
                            if (s.place == entry.place) s.copy(amountMinor = value) else s
                        },
                    )
                }
            },
            label = stringResource(savingsPlaceLabel(entry.place)),
            supportingText = stringResource(R.string.survey_optional),
        )
    }
}

@StringRes
fun savingsPlaceLabel(place: SavingsPlace): Int = when (place) {
    SavingsPlace.CASH -> R.string.savings_cash
    SavingsPlace.SAVINGS_ACCOUNT -> R.string.savings_account
    SavingsPlace.DEPOSITS -> R.string.savings_deposits
    SavingsPlace.INVESTMENTS -> R.string.savings_investments
}

@Composable
private fun AssetsStep(draft: SurveyDraft, errors: List<SurveyError>, onChange: DraftUpdate) {
    StepHeader(R.string.survey_title_assets, R.string.survey_why_assets)
    Text(stringResource(R.string.survey_assets_hint), style = MaterialTheme.typography.bodyMedium)
    AmountRowsEditor(
        rows = draft.assets,
        onRowsChange = { rows -> onChange { it.copy(assets = rows) } },
        nameLabel = stringResource(R.string.survey_asset_name),
        amountLabel = stringResource(R.string.survey_asset_value),
        addLabel = stringResource(R.string.survey_add_asset),
        errors = errors,
        field = SurveyField.ASSET,
    )
    SwitchRow(
        checked = draft.includeAssetsInCapital,
        onCheckedChange = { value -> onChange { it.copy(includeAssetsInCapital = value) } },
        label = stringResource(R.string.survey_assets_include),
        description = stringResource(R.string.survey_assets_include_hint),
    )
}

@Composable
private fun MainGoalStep(draft: SurveyDraft, errors: List<SurveyError>, onChange: DraftUpdate) {
    StepHeader(R.string.survey_title_main_goal, R.string.survey_why_main_goal)
    val goal = draft.mainGoal
    MainGoalKind.entries.forEach { kind ->
        RadioOption(
            selected = goal.kind == kind,
            onSelect = { onChange { it.copy(mainGoal = it.mainGoal.copy(kind = kind)) } },
            label = stringResource(mainGoalTitleRes(kind)),
        )
    }
    if (goal.kind == MainGoalKind.OTHER) {
        val titleError = errors.forField(SurveyField.MAIN_GOAL_TITLE)
        OutlinedTextField(
            value = goal.customTitle,
            onValueChange = { text -> onChange { it.copy(mainGoal = it.mainGoal.copy(customTitle = text)) } },
            label = { Text(stringResource(R.string.survey_goal_title)) },
            isError = titleError != null,
            supportingText = titleError?.let { { Text(it.message()) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (goal.kind != null) {
        val targetError = errors.forField(SurveyField.MAIN_GOAL_TARGET)
        MoneyField(
            valueMinor = goal.targetMinor,
            onValueChange = { value -> onChange { it.copy(mainGoal = it.mainGoal.copy(targetMinor = value)) } },
            label = stringResource(R.string.survey_goal_target),
            supportingText = targetError?.message() ?: stringResource(R.string.survey_optional),
            isError = targetError != null,
        )
        DateField(
            label = stringResource(R.string.survey_goal_deadline),
            date = goal.deadline,
            onDateChange = { date -> onChange { it.copy(mainGoal = it.mainGoal.copy(deadline = date)) } },
            error = errors.forField(SurveyField.MAIN_GOAL_DEADLINE),
        )
    }
}

@Composable
private fun MidGoalsStep(draft: SurveyDraft, errors: List<SurveyError>, onChange: DraftUpdate) {
    StepHeader(R.string.survey_title_mid_goals, R.string.survey_why_mid_goals)
    Text(stringResource(R.string.survey_mid_goals_hint), style = MaterialTheme.typography.bodyMedium)
    draft.midGoals.forEachIndexed { index, goal ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = goal.title,
                    onValueChange = { text -> onChange { it.updateMid(index) { g -> g.copy(title = text) } } },
                    label = { Text(stringResource(R.string.survey_goal_title)) },
                    isError = errors.forField(SurveyField.MID_GOAL_TITLE, index) != null,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    onChange {
                        it.copy(
                            midGoals = it.midGoals.filterIndexed {
                                    i,
                                    _,
                                ->
                                i != index
                            },
                        )
                    }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete_row))
                }
            }
            val targetError = errors.forField(SurveyField.MID_GOAL_TARGET, index)
            MoneyField(
                valueMinor = goal.targetMinor,
                onValueChange = { value -> onChange { it.updateMid(index) { g -> g.copy(targetMinor = value) } } },
                label = stringResource(R.string.survey_goal_target),
                isError = targetError != null,
                supportingText = targetError?.message(),
            )
            DateField(
                label = stringResource(R.string.survey_goal_deadline),
                date = goal.deadline,
                onDateChange = { date -> onChange { it.updateMid(index) { g -> g.copy(deadline = date) } } },
                error = errors.forField(SurveyField.MID_GOAL_DEADLINE, index),
            )
        }
    }
    TextButton(onClick = { onChange { it.copy(midGoals = it.midGoals + GoalDraft()) } }) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(stringResource(R.string.survey_add_goal), Modifier.padding(start = 8.dp))
    }
}

private fun SurveyDraft.updateMid(index: Int, transform: (GoalDraft) -> GoalDraft): SurveyDraft =
    copy(midGoals = midGoals.mapIndexed { i, g -> if (i == index) transform(g) else g })

@Composable
private fun HabitsStep(
    draft: SurveyDraft,
    onChange: DraftUpdate,
    onReminderPicked: (ReminderMode) -> Unit,
    showReminder: Boolean,
) {
    StepHeader(R.string.survey_title_habits, R.string.survey_why_habits)
    Text(stringResource(R.string.survey_experience_title), style = MaterialTheme.typography.titleMedium)
    BookkeepingExperience.entries.forEach { value ->
        RadioOption(
            selected = draft.experience == value,
            onSelect = { onChange { it.copy(experience = value) } },
            label = stringResource(experienceLabel(value)),
        )
    }
    Text(stringResource(R.string.survey_time_title), style = MaterialTheme.typography.titleMedium)
    TimeCommitment.entries.forEach { value ->
        RadioOption(
            selected = draft.timeCommitment == value,
            onSelect = { onChange { it.copy(timeCommitment = value) } },
            label = stringResource(timeCommitmentLabel(value)),
        )
    }
    if (!showReminder) return
    Text(stringResource(R.string.survey_reminder_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.survey_reminder_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val effective = draft.reminderMode ?: ReminderDefaults.suggested(draft.timeCommitment)
    ReminderMode.entries.forEach { mode ->
        RadioOption(
            selected = effective == mode,
            onSelect = {
                onChange { it.copy(reminderMode = mode) }
                onReminderPicked(mode)
            },
            label = stringResource(reminderModeLabel(mode)),
        )
    }
}

@StringRes
fun experienceLabel(value: BookkeepingExperience): Int = when (value) {
    BookkeepingExperience.NEVER -> R.string.experience_never
    BookkeepingExperience.EXCEL -> R.string.experience_excel
    BookkeepingExperience.APPS -> R.string.experience_apps
    BookkeepingExperience.QUIT -> R.string.experience_quit
}

@StringRes
fun timeCommitmentLabel(value: TimeCommitment): Int = when (value) {
    TimeCommitment.DAILY -> R.string.time_daily
    TimeCommitment.FEW_MINUTES_DAILY -> R.string.time_few_minutes
    TimeCommitment.WEEKLY -> R.string.time_weekly
    TimeCommitment.MONTHLY -> R.string.time_monthly
}

@StringRes
fun reminderModeLabel(mode: ReminderMode): Int = when (mode) {
    ReminderMode.DAILY_EVENING -> R.string.reminder_daily
    ReminderMode.WEEKLY -> R.string.reminder_weekly
    ReminderMode.OFF -> R.string.reminder_off
}

@Composable
private fun ErrorLine(error: SurveyError) {
    Text(error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
