package app.fingramm.domain.backup

import app.fingramm.domain.model.AccountType
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryKind
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.Transaction
import app.fingramm.domain.model.TransactionType

/** Заголовки CSV-файла; передаются снаружи, чтобы строки жили в ресурсах приложения. */
public data class CsvHeaders(
    val date: String,
    val type: String,
    val amount: String,
    val currency: String,
    val category: String,
    val kind: String,
    val account: String,
    val note: String,
)

/** Подписи значений перечислений для CSV. */
public data class CsvLabels(
    val income: String,
    val expense: String,
    val mandatory: String,
    val variable: String,
    val cash: String,
    val card: String,
    val savings: String,
)

/**
 * Экспорт операций в CSV для Excel/Google Таблиц с русской локалью: разделитель «;»,
 * десятичная запятая, UTF-8 с BOM. Ячейки, начинающиеся с = + - @, экранируются (защита от формул).
 */
public object CsvExporter {
    private val BOM = 0xFEFF.toChar().toString()
    private const val DELIMITER = ";"
    private const val EOL = "\r\n"
    private const val MINOR_PER_MAJOR = 100

    public fun export(
        transactions: List<Transaction>,
        categories: List<Category>,
        currency: CurrencyCode,
        headers: CsvHeaders,
        labels: CsvLabels,
    ): String {
        val byId = categories.associateBy { it.id }
        val sb = StringBuilder(BOM)
        sb.append(
            listOf(
                headers.date,
                headers.type,
                headers.amount,
                headers.currency,
                headers.category,
                headers.kind,
                headers.account,
                headers.note,
            ).joinToString(DELIMITER) { cell(it) },
        ).append(EOL)
        transactions.sortedWith(compareBy<Transaction>({ it.date }, { it.id })).forEach { t ->
            val category = byId[t.categoryId]
            val kind = when {
                t.type == TransactionType.INCOME -> ""
                category?.kind == CategoryKind.MANDATORY -> labels.mandatory
                else -> labels.variable
            }
            sb.append(
                listOf(
                    t.date.toString(),
                    if (t.type == TransactionType.INCOME) labels.income else labels.expense,
                    amount(t.amountMinor),
                    currency.code,
                    category?.name.orEmpty(),
                    kind,
                    when (t.accountType) {
                        AccountType.CASH -> labels.cash
                        AccountType.CARD -> labels.card
                        AccountType.SAVINGS -> labels.savings
                        null -> ""
                    },
                    t.note,
                ).joinToString(DELIMITER) { cell(it) },
            ).append(EOL)
        }
        return sb.toString()
    }

    internal fun amount(minor: Long): String {
        val major = minor / MINOR_PER_MAJOR
        val fraction = Math.abs(minor % MINOR_PER_MAJOR)
        val sign = if (minor < 0 && major == 0L) "-" else ""
        return "$sign$major,${fraction.toString().padStart(2, '0')}"
    }

    internal fun cell(value: String): String {
        var text = value.replace("\r\n", "\n")
        if (text.isNotEmpty() && text.first() in "=+-@\t") text = "'$text"
        val needsQuotes = text.any { it == '"' || it == ';' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }
}
