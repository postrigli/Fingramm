package app.fingramm.widget

import android.content.Context
import android.widget.RemoteViews
import app.fingramm.R
import app.fingramm.domain.money.Money
import app.fingramm.ui.common.formatMonth

/** Строит RemoteViews виджета месяца по [MonthWidgetState]; чистая функция, без обращения к данным. */
object MonthWidgetRenderer {
    /** Вместо сумм при включённой блокировке. */
    const val HIDDEN = "••••"

    fun render(context: Context, state: MonthWidgetState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_month)
        val title = formatMonth(state.month).replaceFirstChar { it.uppercase() }
        views.setTextViewText(R.id.widget_month, title)

        val income: String
        val expenses: String
        val balance: String
        val description: String
        when {
            !state.configured -> {
                income = EMPTY
                expenses = EMPTY
                balance = EMPTY
                description = context.getString(R.string.widget_stats_empty_cd)
            }
            state.amountsHidden -> {
                income = HIDDEN
                expenses = HIDDEN
                balance = HIDDEN
                description = context.getString(R.string.widget_stats_hidden_cd, title)
            }
            else -> {
                income = whole(state.incomeMinor, state)
                expenses = whole(state.expensesMinor, state)
                balance = whole(state.balanceMinor, state)
                description = context.getString(R.string.widget_stats_cd, title, income, expenses, balance)
            }
        }
        views.setTextViewText(R.id.widget_income_value, income)
        views.setTextViewText(R.id.widget_expense_value, expenses)
        views.setTextViewText(R.id.widget_balance_value, balance)
        views.setContentDescription(R.id.widget_stats, description)

        QuickAddWidgetProvider.bindButtons(context, views)
        QuickAddWidgetProvider.bindDashboard(context, views, R.id.widget_stats)
        return views
    }

    /** На виджете места мало, поэтому суммы без копеек (отбрасываются, а не округляются: 99,99 → 99). */
    private fun whole(minor: Long, state: MonthWidgetState): String =
        Money.format(minor / Money.MINOR_PER_MAJOR * Money.MINOR_PER_MAJOR, state.currency)

    private const val EMPTY = "—"
}
