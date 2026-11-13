package app.fingramm.ui

import android.content.Context
import app.fingramm.R
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.DefaultCategories
import app.fingramm.domain.model.PresetCategory

/** Предустановленные категории с названиями из ресурсов; в БД попадают уже локализованные. */
object CategoryPresets {
    private val names =
        mapOf(
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
            "tobacco" to R.string.cat_tobacco,
            "alcohol" to R.string.cat_alcohol,
            "beauty" to R.string.cat_beauty,
            "sport" to R.string.cat_sport,
            "marketplaces" to R.string.cat_marketplaces,
            "fastfood" to R.string.cat_fastfood,
            "fuel" to R.string.cat_fuel,
            "auto" to R.string.cat_auto,
            "home_repair" to R.string.cat_home_repair,
            "electronics" to R.string.cat_electronics,
            "household" to R.string.cat_household,
            "pets" to R.string.cat_pets,
            "gifts" to R.string.cat_gifts,
            "travel" to R.string.cat_travel,
            "bonus" to R.string.cat_bonus,
            "interest" to R.string.cat_interest,
            "benefits" to R.string.cat_benefits,
            "sale_items" to R.string.cat_sale_items,
            "gifts_income" to R.string.cat_gifts_income,
        )

    /** Все предустановленные категории. */
    fun build(context: Context): List<Category> = DefaultCategories.all.map { toCategory(context, it) }

    /** Только появившиеся после набора версии [version]: для тех, кто уже пользуется приложением. */
    fun buildAfter(
        context: Context,
        version: Int,
    ): List<Category> = DefaultCategories.addedAfter(version).map { toCategory(context, it) }

    /** Локализованное название предустановленной категории по ключу. */
    fun name(
        context: Context,
        key: String,
    ): String = context.getString(names.getValue(key))

    private fun toCategory(
        context: Context,
        preset: PresetCategory,
    ) = Category(
        name = context.getString(names.getValue(preset.key)),
        type = preset.type,
        kind = preset.kind,
        icon = preset.icon,
        isDebtPayment = preset.isDebtPayment,
    )
}
