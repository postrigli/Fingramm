package app.fingramm.domain.model

/** Предустановленная категория. [key] сопоставляется со строкой названия в ресурсах приложения. */
public data class PresetCategory(
    val key: String,
    val type: TransactionType,
    val kind: CategoryKind,
    val icon: String,
    val isDebtPayment: Boolean = false,
)

public object DefaultCategories {
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
    )
}
