package app.fingramm.ui.onboarding

import android.content.Context
import app.fingramm.R
import app.fingramm.domain.model.MainGoalKind
import app.fingramm.domain.survey.DefaultNames
import app.fingramm.domain.survey.SurveyDraft
import app.fingramm.domain.survey.SurveyError
import app.fingramm.domain.survey.SurveyMapper
import app.fingramm.domain.survey.SurveyResult
import app.fingramm.domain.survey.SurveyStep
import app.fingramm.domain.survey.SurveyValidation
import java.time.LocalDate

/** Состояние формы опроса, общее для мастера онбординга и экрана редактирования ответов. */
data class SurveyFormState(
    val draft: SurveyDraft = SurveyDraft(),
    val step: SurveyStep = SurveyStep.WELCOME,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val loaded: Boolean = true,
) {
    fun errorsFor(step: SurveyStep, today: LocalDate): List<SurveyError> =
        SurveyValidation.validate(step, draft, today)
}

/** Сборка сущностей из черновика с локализованными названиями по умолчанию. */
internal fun buildSurveyResult(
    context: Context,
    draft: SurveyDraft,
    today: LocalDate,
    createdAt: LocalDate,
): SurveyResult =
    SurveyMapper.toResult(
        draft = draft,
        today = today,
        createdAt = createdAt,
        defaultNames = DefaultNames(
            income = context.getString(R.string.default_income_name),
            asset = context.getString(R.string.default_asset_name),
            debt = context.getString(R.string.default_debt_name),
        ),
        goalTitle = { kind -> context.getString(mainGoalTitleRes(kind)) },
    )

internal fun mainGoalTitleRes(kind: MainGoalKind): Int = when (kind) {
    MainGoalKind.CUSHION -> R.string.goal_kind_cushion
    MainGoalKind.PAY_OFF_DEBT -> R.string.goal_kind_debt
    MainGoalKind.VACATION -> R.string.goal_kind_vacation
    MainGoalKind.START_INVESTING -> R.string.goal_kind_invest
    MainGoalKind.OTHER -> R.string.goal_kind_other
}
