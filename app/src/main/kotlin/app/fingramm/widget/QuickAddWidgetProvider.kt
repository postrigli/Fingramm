package app.fingramm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import app.fingramm.R
import app.fingramm.domain.model.TransactionType

/**
 * Виджет 2×2 «Добавить операцию»: две кнопки, «Расход» и «Доход». Данных не читает и не показывает,
 * состояния у него нет, обновление по расписанию не нужно.
 */
class QuickAddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildViews(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    companion object {
        private const val REQUEST_EXPENSE = 1
        private const val REQUEST_INCOME = 2
        private const val REQUEST_DASHBOARD = 3

        fun buildViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_quick_add).also { bindButtons(context, it) }

        /** Привязывает кнопки «Расход» и «Доход» (общие для обоих виджетов) к открытию формы операции. */
        internal fun bindButtons(context: Context, views: RemoteViews) {
            views.setOnClickPendingIntent(
                R.id.widget_btn_expense,
                pending(context, REQUEST_EXPENSE, QuickAction.AddTransaction(TransactionType.EXPENSE)),
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_income,
                pending(context, REQUEST_INCOME, QuickAction.AddTransaction(TransactionType.INCOME)),
            )
        }

        /** Нажатие на показатели месяца открывает сводку. */
        internal fun bindDashboard(context: Context, views: RemoteViews, viewId: Int) {
            views.setOnClickPendingIntent(viewId, pending(context, REQUEST_DASHBOARD, QuickAction.OpenDashboard))
        }

        private fun pending(context: Context, requestCode: Int, action: QuickAction): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                QuickAction.launchIntent(context, action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
