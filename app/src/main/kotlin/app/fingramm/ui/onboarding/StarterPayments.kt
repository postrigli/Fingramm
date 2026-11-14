package app.fingramm.ui.onboarding

import app.fingramm.R
import app.fingramm.domain.model.TransactionType

/** Типовой регулярный платёж на стартовом экране. [categoryKey] — ключ предустановленной категории. */
data class StarterPayment(
    val categoryKey: String,
    val titleRes: Int,
    val type: TransactionType,
    val defaultDay: Int,
)

object StarterPayments {
    val all: List<StarterPayment> =
        listOf(
            StarterPayment("rent_mortgage", R.string.starter_rent, TransactionType.EXPENSE, 1),
            StarterPayment("utilities", R.string.starter_utilities, TransactionType.EXPENSE, 20),
            StarterPayment("communication", R.string.starter_communication, TransactionType.EXPENSE, 5),
            StarterPayment("loans", R.string.starter_loan, TransactionType.EXPENSE, 10),
            StarterPayment("subscriptions", R.string.starter_subscriptions, TransactionType.EXPENSE, 1),
        )
}
