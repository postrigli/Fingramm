package app.fingramm.ui

import android.content.Context
import app.fingramm.R
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.DefaultCategories

/** Предустановленные категории с названиями из ресурсов; в БД попадают уже локализованные. */
object CategoryPresets {
    private val names = mapOf(
        "groceries" to R.string.cat_groceries,
        "utilities" to R.string.cat_utilities,
        "transport" to R.string.cat_transport,
        "communication" to R.string.cat_communication,
        "rent_mortgage" to R.string.cat_rent_mortgage,
        "loans" to R.string.cat_loans,
        "health" to R.string.cat_health,
        "kids" to R.string.cat_kids,
        "clothes" to R.string.cat_clothes,
        "entertainment" to R.string.cat_entertainment,
        "cafe" to R.string.cat_cafe,
        "subscriptions" to R.string.cat_subscriptions,
        "education" to R.string.cat_education,
        "other_expense" to R.string.cat_other_expense,
        "salary" to R.string.cat_salary,
        "side_income" to R.string.cat_side_income,
        "rent_income" to R.string.cat_rent_income,
        "other_income" to R.string.cat_other_income,
    )

    fun build(context: Context): List<Category> = DefaultCategories.all.map { preset ->
        Category(
            name = context.getString(names.getValue(preset.key)),
            type = preset.type,
            kind = preset.kind,
            icon = preset.icon,
            isDebtPayment = preset.isDebtPayment,
        )
    }
}
