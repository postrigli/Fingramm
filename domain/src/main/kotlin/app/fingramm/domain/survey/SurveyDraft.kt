package app.fingramm.domain.survey

import app.fingramm.domain.model.Asset
import app.fingramm.domain.model.BookkeepingExperience
import app.fingramm.domain.model.BudgetProfile
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.Debt
import app.fingramm.domain.model.Goal
import app.fingramm.domain.model.GoalHorizon
import app.fingramm.domain.model.IncomeSource
import app.fingramm.domain.model.InputMode
import app.fingramm.domain.model.MainGoalKind
import app.fingramm.domain.model.NamedAmount
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.SavingsEntry
import app.fingramm.domain.model.SavingsPlace
import app.fingramm.domain.model.SurveyAnswers
import app.fingramm.domain.model.TimeCommitment
import java.time.LocalDate

/** Строка «название + сумма». Пустая сумма (null) — ещё не введена. */
public data class DraftAmount(val name: String = "", val amountMinor: Long? = null) {
    val isBlank: Boolean get() = name.isBlank() && amountMinor == null
}

public data class ExpenseDraft(
    val mode: InputMode = InputMode.TOTAL,
    val totalMinor: Long? = null,
    val items: List<DraftAmount> = emptyList(),
    /** Только для переменных расходов: «не знаю». */
    val unknown: Boolean = false,
)

public data class SavingsDraft(val place: SavingsPlace, val amountMinor: Long? = null)

public data class MainGoalDraft(
    val kind: MainGoalKind? = null,
    val customTitle: String = "",
    val targetMinor: Long? = null,
    val deadline: LocalDate? = null,
)

public data class GoalDraft(
    val title: String = "",
    val targetMinor: Long? = null,
    val deadline: LocalDate? = null,
) {
    val isBlank: Boolean get() = title.isBlank() && targetMinor == null && deadline == null
}

/** Все ответы опроса в «сыром» виде: null означает, что поле не заполнено. */
public data class SurveyDraft(
    val currency: CurrencyCode = CurrencyCode.RUB,
    val familySize: Int? = null,
    val hasChildren: Boolean? = null,
    val childrenCount: Int? = null,
    val incomeSources: List<DraftAmount> = listOf(DraftAmount()),
    val mandatory: ExpenseDraft = ExpenseDraft(),
    val variable: ExpenseDraft = ExpenseDraft(),
    val hasDebts: Boolean? = null,
    val debtPaymentMinor: Long? = null,
    val debtBalanceMinor: Long? = null,
    val hasCushion: Boolean? = null,
    val cushionMinor: Long? = null,
    val savings: List<SavingsDraft> = emptyList(),
    val assets: List<DraftAmount> = emptyList(),
    val includeAssetsInCapital: Boolean = false,
    val mainGoal: MainGoalDraft = MainGoalDraft(),
    val midGoals: List<GoalDraft> = emptyList(),
    val experience: BookkeepingExperience? = null,
    val timeCommitment: TimeCommitment? = null,
    val reminderMode: ReminderMode? = null,
)

/** Готовые к сохранению сущности, собранные из черновика. */
public data class SurveyResult(
    val profile: BudgetProfile,
    val survey: SurveyAnswers,
    val incomeSources: List<IncomeSource>,
    val assets: List<Asset>,
    val debts: List<Debt>,
    val goals: List<Goal>,
)

public object SurveyMapper {
    /**
     * Собирает сущности из валидного черновика.
     * [defaultNames] — названия для строк без названия (источник дохода, актив, долги);
     * [goalTitle] — названия предустановленных главных целей.
     */
    public fun toResult(
        draft: SurveyDraft,
        today: LocalDate,
        createdAt: LocalDate = today,
        defaultNames: DefaultNames,
        goalTitle: (MainGoalKind) -> String,
    ): SurveyResult {
        val hasChildren = draft.hasChildren == true
        val profile = BudgetProfile(
            familySize = requireNotNull(draft.familySize) { "familySize" },
            hasChildren = hasChildren,
            childrenCount = if (hasChildren) draft.childrenCount ?: 0 else 0,
            currency = draft.currency,
            createdAt = createdAt,
        )
        val hasCushion = draft.hasCushion == true
        val survey = SurveyAnswers(
            mandatoryMode = draft.mandatory.mode,
            mandatoryTotalMinor = draft.mandatory.totalMinor ?: 0,
            mandatoryItems = draft.mandatory.items.toNamed(),
            variableMode = draft.variable.mode,
            variableTotalMinor = draft.variable.totalMinor ?: 0,
            variableItems = draft.variable.items.toNamed(),
            variableUnknown = draft.variable.unknown,
            hasCushion = hasCushion,
            cushionMinor = if (hasCushion) draft.cushionMinor ?: 0 else 0,
            savings = draft.savings.map { SavingsEntry(it.place, it.amountMinor ?: 0) },
            includeAssetsInCapital = draft.includeAssetsInCapital,
            experience = draft.experience,
            timeCommitment = draft.timeCommitment,
        )
        val income = draft.incomeSources.filter { it.amountMinor != null }
            .map { IncomeSource(name = it.name.ifBlank { defaultNames.income }, amountMinor = it.amountMinor ?: 0) }
        val assets = draft.assets.filter { it.amountMinor != null }
            .map { Asset(name = it.name.ifBlank { defaultNames.asset }, valueMinor = it.amountMinor ?: 0) }
        val debts = if (draft.hasDebts == true) {
            listOf(
                Debt(
                    name = defaultNames.debt,
                    monthlyPaymentMinor = draft.debtPaymentMinor ?: 0,
                    balanceMinor = draft.debtBalanceMinor,
                ),
            )
        } else {
            emptyList()
        }
        return SurveyResult(profile, survey, income, assets, debts, goals(draft, survey, today, goalTitle))
    }

    private fun goals(
        draft: SurveyDraft,
        survey: SurveyAnswers,
        today: LocalDate,
        goalTitle: (MainGoalKind) -> String,
    ): List<Goal> {
        val result = mutableListOf<Goal>()
        val main = draft.mainGoal
        if (main.kind != null) {
            val title = if (main.kind == MainGoalKind.OTHER) main.customTitle.trim() else goalTitle(main.kind)
            result += Goal(
                title = title,
                targetMinor = main.targetMinor,
                // Если главная цель — подушка, уже накопленная подушка засчитывается в прогресс.
                savedMinor = if (main.kind == MainGoalKind.CUSHION) survey.cushionMinor else 0,
                deadline = main.deadline,
                horizon = GoalHorizon.YEAR,
                createdAt = today,
            )
        }
        draft.midGoals.filterNot { it.isBlank }.forEach {
            result += Goal(
                title = it.title.trim(),
                targetMinor = it.targetMinor,
                deadline = it.deadline,
                horizon = GoalHorizon.MID,
                createdAt = today,
            )
        }
        return result
    }

    /** Обратное преобразование для редактирования ответов. Цели в черновик не попадают. */
    public fun toDraft(
        profile: BudgetProfile,
        survey: SurveyAnswers,
        incomeSources: List<IncomeSource>,
        assets: List<Asset>,
        debts: List<Debt>,
    ): SurveyDraft = SurveyDraft(
        currency = profile.currency,
        familySize = profile.familySize,
        hasChildren = profile.hasChildren,
        childrenCount = profile.childrenCount.takeIf { profile.hasChildren },
        incomeSources = incomeSources.map { DraftAmount(it.name, it.amountMinor) }.ifEmpty { listOf(DraftAmount()) },
        mandatory = ExpenseDraft(
            mode = survey.mandatoryMode,
            totalMinor = survey.mandatoryTotalMinor,
            items = survey.mandatoryItems.map { DraftAmount(it.name, it.amountMinor) },
        ),
        variable = ExpenseDraft(
            mode = survey.variableMode,
            totalMinor = survey.variableTotalMinor.takeUnless { survey.variableUnknown },
            items = survey.variableItems.map { DraftAmount(it.name, it.amountMinor) },
            unknown = survey.variableUnknown,
        ),
        hasDebts = debts.isNotEmpty(),
        debtPaymentMinor = debts.takeIf { it.isNotEmpty() }?.sumOf { it.monthlyPaymentMinor },
        debtBalanceMinor = debts.mapNotNull { it.balanceMinor }.takeIf { it.isNotEmpty() }?.sum(),
        hasCushion = survey.hasCushion,
        cushionMinor = survey.cushionMinor.takeIf { survey.hasCushion },
        savings = survey.savings.map { SavingsDraft(it.place, it.amountMinor) },
        assets = assets.map { DraftAmount(it.name, it.valueMinor) },
        includeAssetsInCapital = survey.includeAssetsInCapital,
        experience = survey.experience,
        timeCommitment = survey.timeCommitment,
    )

    private fun List<DraftAmount>.toNamed(): List<NamedAmount> =
        filter { it.amountMinor != null }.map { NamedAmount(it.name.trim(), it.amountMinor ?: 0) }
}

/** Локализованные названия по умолчанию — подставляются из ресурсов приложения. */
public data class DefaultNames(val income: String, val asset: String, val debt: String)
