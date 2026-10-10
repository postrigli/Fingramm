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
import app.fingramm.data.repo.RecurringRepository
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.money.Money
import app.fingramm.domain.recurring.RecurringSchedule
import app.fingramm.widget.QuickAction
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/**
 * Уведомляет о регулярной операции в день платежа в заданное время и ставит следующее уведомление.
 * Если операцию уже внесли или пропустили до этого момента, уведомления не будет: оно нужно только пока платёж
 * ждёт подтверждения. Без разрешения на уведомления приложение продолжает работать молча.
 */
@HiltWorker
class RecurringReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecurringRepository,
    private val categories: CategoryRepository,
    private val budget: BudgetRepository,
    private val scheduler: RecurringReminderScheduler,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val template = repository.get(inputData.getLong(KEY_TEMPLATE_ID, 0)) ?: return Result.success()
        // Шаблон могли приостановить или снять уведомление уже после постановки задачи.
        if (!template.active || template.notifyAtMinute == null) return Result.success()
        val today = LocalDate.now(clock)
        val waiting = RecurringSchedule.pending(listOf(template), today).any { it.dueDate == today }
        if (waiting && canNotify()) show(template)
        scheduler.scheduleNext(template)
        return Result.success()
    }

    private fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @android.annotation.SuppressLint("MissingPermission") // разрешение проверено в canNotify()
    private suspend fun show(template: RecurringTemplate) {
        ensureChannel(context)
        val category = categories.categories.first().firstOrNull { it.id == template.categoryId }
        val currency = budget.profile.first()?.currency ?: CurrencyCode.RUB
        val sign = if (template.type == TransactionType.INCOME) "+" else "−"
        val title = listOf(category?.name, template.note.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
        val text = context.getString(
            R.string.recurring_notification_text,
            sign + Money.format(template.amountMinor, currency),
        )
        val open = PendingIntent.getActivity(
            context,
            template.id.toInt(),
            QuickAction.launchIntent(context, QuickAction.OpenTransactions),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { context.getString(R.string.recurring_notification_title) })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + template.id.toInt(), notification)
    }

    companion object {
        const val KEY_TEMPLATE_ID = "templateId"
        const val CHANNEL_ID = "recurring"
        private const val NOTIFICATION_ID_BASE = 2000

        /** Канал «Регулярные операции» (Android 8+). Безопасно вызывать повторно. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.recurring_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.recurring_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
