package app.fingramm.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.ReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Планирует локальные напоминания через WorkManager цепочкой одноразовых задач: каждая, отработав,
 * ставит следующую. Задачи WorkManager хранятся в его БД и сами восстанавливаются после перезагрузки.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    /** Применяет настройки: выключает напоминания или перепланирует с учётом текущего времени и пояса. */
    fun apply(settings: ReminderSettings) {
        val workManager = WorkManager.getInstance(context)
        if (settings.mode == ReminderMode.OFF) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = LocalDateTime.now(clock)
        val next = ReminderTiming.nextTrigger(settings, now) ?: return
        val delay = Duration.between(now, next)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "reminder"
    }
}
