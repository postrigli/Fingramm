package app.fingramm.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.fingramm.data.repo.RecurringRepository
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.recurring.RecurringSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Планирует уведомления о регулярных операциях: для каждого шаблона с заданным временем ставится одна задача
 * WorkManager на ближайший момент, а отработав, она ставит следующую (как и для общих напоминаний).
 * Расписание берётся из календаря, а не из `nextDueDate`: уведомление приходит в день платежа в заданное время.
 */
@Singleton
class RecurringReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RecurringRepository,
    private val clock: Clock,
) {
    /**
     * Приводит все уведомления в соответствие с шаблонами: лишние задачи (удалённые, приостановленные, без времени)
     * отменяются, остальные ставятся заново. Вызывается при запуске приложения и после любых правок шаблонов,
     * импорта и удаления данных.
     */
    suspend fun syncAll() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(TAG)
        repository.templates.first().forEach { scheduleNext(it) }
    }

    /** Ставит следующее уведомление шаблона (если оно положено) строго после текущего момента. */
    fun scheduleNext(template: RecurringTemplate) {
        val now = LocalDateTime.now(clock)
        val next = RecurringSchedule.nextNotification(template, now) ?: return
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<RecurringReminderWorker>()
            .setInputData(Data.Builder().putLong(RecurringReminderWorker.KEY_TEMPLATE_ID, template.id).build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(template.id), ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val TAG = "recurring-reminder"

        fun workName(templateId: Long) = "recurring-reminder-$templateId"
    }
}
