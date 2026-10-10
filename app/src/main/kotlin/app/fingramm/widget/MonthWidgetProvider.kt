package app.fingramm.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun stateLoader(): WidgetStateLoader
}

/**
 * Виджет 4×2 с показателями текущего месяца и кнопками «Расход»/«Доход».
 *
 * Обновляется: при добавлении, и при изменении данных, пока приложение запущено ([WidgetRefresher]); раз в час
 * по запросу системы; при смене даты, времени и часового пояса, чтобы в полночь 1 числа месяц сменился сразу.
 */
class MonthWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in CLOCK_ACTIONS) refresh(context)
    }

    /** БД читается вне главного потока; goAsync не даёт системе убить процесс, пока обновление не закончено. */
    private fun refresh(context: Context) {
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                val loader = EntryPointAccessors.fromApplication(app, WidgetEntryPoint::class.java).stateLoader()
                updateAll(app, loader.load())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val CLOCK_ACTIONS = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )

        /** Перерисовывает все размещённые виджеты месяца; без размещённых ничего не делает. */
        fun updateAll(context: Context, state: MonthWidgetState) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MonthWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, MonthWidgetRenderer.render(context, state))
        }
    }
}
