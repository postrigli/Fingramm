package app.fingramm.domain.model

/** Предустановленная категория. [key] сопоставляется со строкой названия в ресурсах приложения. */
public data class PresetCategory(
    val key: String,
    val type: TransactionType,
    val kind: CategoryKind,
    val icon: String,
    val isDebtPayment: Boolean = false,
    /** Версия набора, в которой категория появилась: при обновлении приложения добавляются только более новые. */
    val since: Int = 1,
)

public object DefaultCategories {
    /** Версия набора предустановленных категорий; растёт, когда в набор добавляются новые. */
    public const val CURRENT_VERSION: Int = 2

    public val all: List<PresetCategory> = listOf(
        PresetCategory("groceries", TransactionType.EXPENSE, CategoryKind.MANDATORY, "🛒"),
        PresetCategory("utilities", TransactionType.EXPENSE, CategoryKind.MANDATORY, "💡"),
        PresetCategory("transport", TransactionType.EXPENSE, CategoryKind.MANDATORY, "🚌"),
        PresetCategory("communication", TransactionType.EXPENSE, CategoryKind.MANDATORY, "📱"),
        PresetCategory("rent_mortgage", TransactionType.EXPENSE, CategoryKind.MANDATORY, "🏠"),
        PresetCategory("loans", TransactionType.EXPENSE, CategoryKind.MANDATORY, "💳", isDebtPayment = true),
        PresetCategory("health", TransactionType.EXPENSE, CategoryKind.MANDATORY, "💊"),
        PresetCategory("kids", TransactionType.EXPENSE, CategoryKind.MANDATORY, "🧸"),
        PresetCategory("clothes", TransactionType.EXPENSE, CategoryKind.VARIABLE, "👕"),
        PresetCategory("entertainment", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🎬"),
        PresetCategory("cafe", TransactionType.EXPENSE, CategoryKind.VARIABLE, "☕"),
        PresetCategory("subscriptions", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🔁"),
        PresetCategory("education", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🎓"),
        PresetCategory("other_expense", TransactionType.EXPENSE, CategoryKind.VARIABLE, "📦"),
        PresetCategory("salary", TransactionType.INCOME, CategoryKind.VARIABLE, "💼"),
        PresetCategory("side_income", TransactionType.INCOME, CategoryKind.VARIABLE, "🛠"),
        PresetCategory("rent_income", TransactionType.INCOME, CategoryKind.VARIABLE, "🏢"),
        PresetCategory("other_income", TransactionType.INCOME, CategoryKind.VARIABLE, "💰"),
        PresetCategory("tobacco", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🚬", since = 2),
        PresetCategory("alcohol", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🍷", since = 2),
        PresetCategory("beauty", TransactionType.EXPENSE, CategoryKind.VARIABLE, "💅", since = 2),
        PresetCategory("sport", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🏃", since = 2),
        PresetCategory("marketplaces", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🛍", since = 2),
        PresetCategory("fastfood", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🍔", since = 2),
        PresetCategory("fuel", TransactionType.EXPENSE, CategoryKind.MANDATORY, "⛽", since = 2),
        PresetCategory("auto", TransactionType.EXPENSE, CategoryKind.MANDATORY, "🚗", since = 2),
        PresetCategory("home_repair", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🛋", since = 2),
        PresetCategory("electronics", TransactionType.EXPENSE, CategoryKind.VARIABLE, "💻", since = 2),
        PresetCategory("household", TransactionType.EXPENSE, CategoryKind.MANDATORY, "🧽", since = 2),
        PresetCategory("pets", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🐾", since = 2),
        PresetCategory("gifts", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🎁", since = 2),
        PresetCategory("travel", TransactionType.EXPENSE, CategoryKind.VARIABLE, "✈", since = 2),
        PresetCategory("bonus", TransactionType.INCOME, CategoryKind.VARIABLE, "🏆", since = 2),
        PresetCategory("interest", TransactionType.INCOME, CategoryKind.VARIABLE, "📈", since = 2),
        PresetCategory("benefits", TransactionType.INCOME, CategoryKind.VARIABLE, "🏛", since = 2),
        PresetCategory("sale_items", TransactionType.INCOME, CategoryKind.VARIABLE, "🏷", since = 2),
        PresetCategory("gifts_income", TransactionType.INCOME, CategoryKind.VARIABLE, "🤝", since = 2),
    )

    /** Категории, появившиеся после набора версии [version]. */
    public fun addedAfter(version: Int): List<PresetCategory> = all.filter { it.since > version }
}
