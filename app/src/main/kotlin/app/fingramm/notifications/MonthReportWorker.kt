package app.fingramm.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.fingramm.R
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.data.repo.StatsRepository
import app.fingramm.domain.report.ReportTiming
import app.fingramm.domain.stats.StatsCalculator
import app.fingramm.ui.common.formatPeriod
import app.fingramm.widget.QuickAction
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * В первый день нового расчётного месяца сообщает, что отчёт за прошедший готов, и ставит следующее уведомление.
 * Если за период не было операций или приложение ещё не настроено, молчит.
 */
@HiltWorker
class MonthReportWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted params: WorkerParameters,
        private val settings: SettingsRepository,
        private val budget: BudgetRepository,
        private val stats: StatsRepository,
        private val categories: CategoryRepository,
        private val scheduler: MonthReportScheduler,
        private val clock: Clock,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val startDay = settings.current().monthStartDay
            val closed = ReportTiming.closedPeriod(LocalDate.now(clock), startDay)
            if (budget.profile.first() != null && hasData(closed) && canNotify()) show(closed, startDay)
            scheduler.schedule()
            return Result.success()
        }

        private suspend fun hasData(period: YearMonth): Boolean {
            val byId = categories.categories.first().associateBy { it.id }
            return StatsCalculator.monthTotals(stats.monthCategoryTotals.first(), byId)[period]?.hasData == true
        }

        private fun canNotify(): Boolean {
            val granted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        @android.annotation.SuppressLint("MissingPermission") // разрешение проверено в canNotify()
        private fun show(period: YearMonth, startDay: Int) {
            ensureChannel(context)
            val open =
                PendingIntent.getActivity(
                    context,
                    REQUEST_CODE,
                    QuickAction.launchIntent(context, QuickAction.OpenReport(period), NOTIFICATION_ID),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val title = context.getString(R.string.report_notification_title, formatPeriod(period, startDay))
            val text = context.getString(R.string.report_notification_text)
            val notification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }

        companion object {
            const val CHANNEL_ID = "reports"
            private const val NOTIFICATION_ID = 3000
            private const val REQUEST_CODE = 3000

            /** Канал «Отчёты» (Android 8+). Безопасно вызывать повторно. */
            fun ensureChannel(context: Context) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.report_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = context.getString(R.string.report_channel_description) }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }
    }
