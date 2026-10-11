package app.fingramm.ui.navigation

import app.fingramm.domain.model.TransactionType

/** Маршруты навигации. Параметры редактирования — необязательные query-аргументы (id < 0 — новая запись). */
object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "transactions"
    const val GOALS = "goals"
    const val MORE = "more"

    const val TRANSACTION_EDIT = "transaction/edit?id={id}&type={type}&template={template}"
    const val GOAL_EDIT = "goal/edit?id={id}"
    const val CATEGORIES = "categories"
    const val CATEGORY_EDIT = "category/edit?id={id}&type={type}"
    const val RECURRING = "recurring"
    const val RECURRING_EDIT = "recurring/edit?id={id}"
    const val SURVEY = "survey"
    const val SURVEY_STEP = "survey/{step}"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val ABOUT = "about"

    val topLevel = listOf(DASHBOARD, TRANSACTIONS, GOALS, MORE)

    /**
     * [type] задаёт тип новой операции (из виджета); для существующей записи игнорируется. [template] — id
     * регулярной операции: форма заполняется её данными, а сохранение подтверждает ожидающее списание.
     */
    fun transactionEdit(id: Long? = null, type: TransactionType? = null, template: Long? = null) =
        "transaction/edit?id=${id ?: NEW}" +
            (type?.let { "&type=${it.name}" } ?: "") +
            (template?.let { "&template=$it" } ?: "")

    fun goalEdit(id: Long? = null) = "goal/edit?id=${id ?: NEW}"

    fun categoryEdit(id: Long? = null, type: String = "EXPENSE") = "category/edit?id=${id ?: NEW}&type=$type"

    fun recurringEdit(id: Long? = null) = "recurring/edit?id=${id ?: NEW}"

    fun surveyStep(step: String) = "survey/$step"

    const val NEW = -1L
}
