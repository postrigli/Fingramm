package app.fingramm.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.domain.report.ReportTiming
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Планирует уведомление «Отчёт за месяц готов»: одна задача WorkManager на ближайшие 10:00 первого дня расчётного
 * месяца, отработав, она ставит следующую. Расписание пересчитывается при запуске приложения и при смене дня начала
 * месяца.
 */
@Singleton
class MonthReportScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settings: SettingsRepository,
        private val clock: Clock,
    ) {
        suspend fun schedule() {
            val now = LocalDateTime.now(clock)
            val next = ReportTiming.nextTrigger(now, settings.current().monthStartDay)
            val request =
                OneTimeWorkRequestBuilder<MonthReportWorker>()
                    .setInitialDelay(Duration.between(now, next).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        companion object {
            const val WORK_NAME = "month-report"
        }
    }
