package app.fingramm.domain.survey

import app.fingramm.domain.model.InputMode
import app.fingramm.domain.model.MainGoalKind
import java.time.LocalDate

/** Шаги опроса в порядке прохождения. */
public enum class SurveyStep(public val optional: Boolean) {
    WELCOME(optional = false),
    FAMILY(optional = false),
    INCOME(optional = false),
    MANDATORY(optional = false),
    VARIABLE(optional = false),
    DEBTS(optional = false),
    CUSHION(optional = false),
    ASSETS(optional = true),
    MAIN_GOAL(optional = true),
    MID_GOALS(optional = true),
    HABITS(optional = true),
}

public enum class SurveyField {
    FAMILY_SIZE,
    HAS_CHILDREN,
    CHILDREN_COUNT,
    INCOME,
    MANDATORY,
    VARIABLE,
    HAS_DEBTS,
    DEBT_PAYMENT,
    HAS_CUSHION,
    CUSHION_AMOUNT,
    ASSET,
    MAIN_GOAL_TITLE,
    MAIN_GOAL_TARGET,
    MAIN_GOAL_DEADLINE,
    MID_GOAL_TITLE,
    MID_GOAL_TARGET,
    MID_GOAL_DEADLINE,
}

public enum class SurveyErrorCode { REQUIRED, OUT_OF_RANGE, MUST_BE_POSITIVE, DATE_IN_PAST }

/** Ошибка поля. [index] — номер строки в списках (источники дохода, активы, цели). */
public data class SurveyError(val field: SurveyField, val code: SurveyErrorCode, val index: Int? = null)

public object SurveyValidation {
    public const val MIN_FAMILY_SIZE: Int = 1
    public const val MAX_FAMILY_SIZE: Int = 20

    /** Проверяет только поля указанного шага. Пустой список — шаг заполнен корректно. */
    public fun validate(step: SurveyStep, draft: SurveyDraft, today: LocalDate): List<SurveyError> = when (step) {
        SurveyStep.WELCOME, SurveyStep.HABITS -> emptyList()
        SurveyStep.FAMILY -> family(draft)
        SurveyStep.INCOME -> income(draft)
        SurveyStep.MANDATORY -> expenses(draft.mandatory, SurveyField.MANDATORY, allowUnknown = false)
        SurveyStep.VARIABLE -> expenses(draft.variable, SurveyField.VARIABLE, allowUnknown = true)
        SurveyStep.DEBTS -> debts(draft)
        SurveyStep.CUSHION -> cushion(draft)
        SurveyStep.ASSETS -> assets(draft)
        SurveyStep.MAIN_GOAL -> mainGoal(draft, today)
        SurveyStep.MID_GOALS -> midGoals(draft, today)
    }

    /** Все шаги сразу — для финальной проверки перед сохранением. */
    public fun validateAll(draft: SurveyDraft, today: LocalDate): Map<SurveyStep, List<SurveyError>> =
        SurveyStep.entries.associateWith { validate(it, draft, today) }.filterValues { it.isNotEmpty() }

    private fun family(draft: SurveyDraft): List<SurveyError> {
        val errors = mutableListOf<SurveyError>()
        val size = draft.familySize
        if (size == null) {
            errors += SurveyError(SurveyField.FAMILY_SIZE, SurveyErrorCode.REQUIRED)
        } else if (size !in MIN_FAMILY_SIZE..MAX_FAMILY_SIZE) {
            errors += SurveyError(SurveyField.FAMILY_SIZE, SurveyErrorCode.OUT_OF_RANGE)
        }
        if (draft.hasChildren == null) {
            errors += SurveyError(SurveyField.HAS_CHILDREN, SurveyErrorCode.REQUIRED)
        } else if (draft.hasChildren) {
            val count = draft.childrenCount
            // Хотя бы один взрослый: детей не больше, чем размер семьи − 1.
            if (count == null) {
                errors += SurveyError(SurveyField.CHILDREN_COUNT, SurveyErrorCode.REQUIRED)
            } else if (count < 1 || (size != null && count > size - 1)) {
                errors += SurveyError(SurveyField.CHILDREN_COUNT, SurveyErrorCode.OUT_OF_RANGE)
            }
        }
        return errors
    }

    private fun income(draft: SurveyDraft): List<SurveyError> {
        val rows = draft.incomeSources
        val anyAmount = rows.any { it.amountMinor != null }
        val errors = mutableListOf<SurveyError>()
        if (!anyAmount) errors += SurveyError(SurveyField.INCOME, SurveyErrorCode.REQUIRED)
        rows.forEachIndexed { index, row ->
            if (row.amountMinor == null && row.name.isNotBlank()) {
                errors += SurveyError(SurveyField.INCOME, SurveyErrorCode.REQUIRED, index)
            }
        }
        return errors
    }

    private fun expenses(expense: ExpenseDraft, field: SurveyField, allowUnknown: Boolean): List<SurveyError> {
        if (allowUnknown && expense.unknown) return emptyList()
        return when (expense.mode) {
            InputMode.TOTAL ->
                if (expense.totalMinor == null) listOf(SurveyError(field, SurveyErrorCode.REQUIRED)) else emptyList()
            InputMode.BY_CATEGORY ->
                if (expense.items.none { it.amountMinor != null }) {
                    listOf(SurveyError(field, SurveyErrorCode.REQUIRED))
                } else {
                    emptyList()
                }
        }
    }

    private fun debts(draft: SurveyDraft): List<SurveyError> {
        if (draft.hasDebts == null) return listOf(SurveyError(SurveyField.HAS_DEBTS, SurveyErrorCode.REQUIRED))
        if (draft.hasDebts && draft.debtPaymentMinor == null) {
            return listOf(SurveyError(SurveyField.DEBT_PAYMENT, SurveyErrorCode.REQUIRED))
        }
        return emptyList()
    }

    private fun cushion(draft: SurveyDraft): List<SurveyError> {
        if (draft.hasCushion == null) return listOf(SurveyError(SurveyField.HAS_CUSHION, SurveyErrorCode.REQUIRED))
        if (draft.hasCushion) {
            val amount = draft.cushionMinor
            if (amount == null) return listOf(SurveyError(SurveyField.CUSHION_AMOUNT, SurveyErrorCode.REQUIRED))
            if (amount <= 0) return listOf(SurveyError(SurveyField.CUSHION_AMOUNT, SurveyErrorCode.MUST_BE_POSITIVE))
        }
        return emptyList()
    }

    private fun assets(draft: SurveyDraft): List<SurveyError> = draft.assets.mapIndexedNotNull { index, row ->
        if (row.amountMinor == null && !row.isBlank) {
            SurveyError(SurveyField.ASSET, SurveyErrorCode.REQUIRED, index)
        } else {
            null
        }
    }

    private fun mainGoal(draft: SurveyDraft, today: LocalDate): List<SurveyError> {
        val goal = draft.mainGoal
        val kind = goal.kind ?: return emptyList()
        val errors = mutableListOf<SurveyError>()
        if (kind == MainGoalKind.OTHER && goal.customTitle.isBlank()) {
            errors += SurveyError(SurveyField.MAIN_GOAL_TITLE, SurveyErrorCode.REQUIRED)
        }
        if (goal.targetMinor != null && goal.targetMinor <= 0) {
            errors += SurveyError(SurveyField.MAIN_GOAL_TARGET, SurveyErrorCode.MUST_BE_POSITIVE)
        }
        if (goal.deadline != null && !goal.deadline.isAfter(today)) {
            errors += SurveyError(SurveyField.MAIN_GOAL_DEADLINE, SurveyErrorCode.DATE_IN_PAST)
        }
        return errors
    }

    private fun midGoals(draft: SurveyDraft, today: LocalDate): List<SurveyError> {
        val errors = mutableListOf<SurveyError>()
        draft.midGoals.forEachIndexed { index, goal ->
            if (goal.isBlank) return@forEachIndexed
            if (goal.title.isBlank()) errors += SurveyError(SurveyField.MID_GOAL_TITLE, SurveyErrorCode.REQUIRED, index)
            if (goal.targetMinor == null) {
                errors += SurveyError(SurveyField.MID_GOAL_TARGET, SurveyErrorCode.REQUIRED, index)
            } else if (goal.targetMinor <= 0) {
                errors += SurveyError(SurveyField.MID_GOAL_TARGET, SurveyErrorCode.MUST_BE_POSITIVE, index)
            }
            if (goal.deadline != null && !goal.deadline.isAfter(today)) {
                errors += SurveyError(SurveyField.MID_GOAL_DEADLINE, SurveyErrorCode.DATE_IN_PAST, index)
            }
        }
        return errors
    }
}
