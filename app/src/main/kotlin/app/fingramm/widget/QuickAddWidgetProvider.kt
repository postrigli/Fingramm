package app.fingramm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import app.fingramm.R
import app.fingramm.domain.model.TransactionType

/**
 * Виджет «Добавить операцию». Стандартный размер 4×2: две крупные кнопки «Расход» и «Доход».
 * При растяжении примерно до 5×3 появляется ряд ярлыков «Операции» и «Сводка».
 *
 * Виджет не показывает и не читает суммы: он только открывает приложение (через экран блокировки, если он включён),
 * поэтому приватность данных на рабочем столе не страдает. Состояния у него нет, обновление по расписанию не нужно.
 */
class QuickAddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context, appWidgetManager.getAppWidgetOptions(id)))
        }
    }

    /** Пользователь изменил размер виджета на рабочем столе — выбираем подходящую раскладку. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, newOptions))
    }

    companion object {
        /**
         * Порог высоты (dp), с которого берётся большая раскладка. Две ячейки в высоту — около 110 dp,
         * три — около 180 dp, поэтому 140 dp надёжно отделяет 4×2 от 5×3 и в книжной, и в альбомной ориентации.
         */
        const val LARGE_MIN_HEIGHT_DP = 140

        private const val REQUEST_EXPENSE = 1
        private const val REQUEST_INCOME = 2
        private const val REQUEST_TRANSACTIONS = 3
        private const val REQUEST_DASHBOARD = 4

        fun isLarge(heightDp: Int): Boolean = heightDp >= LARGE_MIN_HEIGHT_DP

        /**
         * Текущая высота виджета в dp. Размеры приходят парой: в книжной ориентации высота — MAX_HEIGHT,
         * в альбомной — MIN_HEIGHT. Если лаунчер размеры не сообщил, возвращается 0 (компактная раскладка).
         */
        fun heightDp(context: Context, options: Bundle?): Int {
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val key = when {
                landscape -> AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                else -> AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            }
            return options?.getInt(key, 0) ?: 0
        }

        fun buildViews(context: Context, options: Bundle?): RemoteViews {
            val large = isLarge(heightDp(context, options))
            val layout = if (large) R.layout.widget_quick_add_large else R.layout.widget_quick_add
            return RemoteViews(context.packageName, layout).apply {
                setOnClickPendingIntent(
                    R.id.widget_btn_expense,
                    pending(context, REQUEST_EXPENSE, QuickAction.AddTransaction(TransactionType.EXPENSE)),
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_income,
                    pending(context, REQUEST_INCOME, QuickAction.AddTransaction(TransactionType.INCOME)),
                )
                if (large) {
                    setOnClickPendingIntent(
                        R.id.widget_btn_transactions,
                        pending(context, REQUEST_TRANSACTIONS, QuickAction.OpenTransactions),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_btn_dashboard,
                        pending(context, REQUEST_DASHBOARD, QuickAction.OpenDashboard),
                    )
                }
            }
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
