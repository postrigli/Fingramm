package app.fingramm.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        val pending = RecurringSchedule.pending(listOf(template), today)
        // Уведомляем, только если платёж наступил именно сегодня; просроченные подхватит тот же список.
        if (pending.any { it.dueDate == today } && canNotify()) show(template, pending.first().dueDate, today)
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
    private suspend fun show(template: RecurringTemplate, dueDate: LocalDate, today: LocalDate) {
        ensureChannel(context)
        val category = categories.categories.first().firstOrNull { it.id == template.categoryId }
        val currency = budget.profile.first()?.currency ?: CurrencyCode.RUB
        val sign = if (template.type == TransactionType.INCOME) "+" else "−"
        val title = listOf(category?.name, template.note.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
            .ifBlank { context.getString(R.string.recurring_notification_title) }
        val amount = sign + Money.format(template.amountMinor, currency)
        val text = if (dueDate == today) {
            amount
        } else {
            context.getString(R.string.recurring_notification_text_overdue, amount, dueDate.format(DATE_FORMAT))
        }
        val notificationId = notificationId(template.id)
        // Публичная версия для экрана блокировки: без суммы и категории.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.recurring_notification_title))
            .setContentText(context.getString(R.string.recurring_notification_public))
            .build()
        val confirm = broadcast(template.id, notificationId, RecurringNotificationReceiver.ACTION_CONFIRM, KIND_CONFIRM)
        val cancel = broadcast(template.id, notificationId, RecurringNotificationReceiver.ACTION_CANCEL, KIND_CANCEL)
        val edit = activity(template.id, notificationId, KIND_EDIT, QuickAction.EditPending(template.id))
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(activity(template.id, notificationId, KIND_OPEN, QuickAction.OpenPending))
            .addAction(0, context.getString(R.string.recurring_action_confirm), confirm)
            .addAction(0, context.getString(R.string.recurring_action_edit), edit)
            .addAction(0, context.getString(R.string.recurring_action_cancel), cancel)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun activity(templateId: Long, notificationId: Int, kind: Int, action: QuickAction): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(templateId, kind),
            QuickAction.launchIntent(context, action, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcast(templateId: Long, notificationId: Int, action: String, kind: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(templateId, kind),
            Intent(context, RecurringNotificationReceiver::class.java)
                .setAction(action)
                .putExtra(RecurringNotificationReceiver.EXTRA_TEMPLATE_ID, templateId)
                .putExtra(RecurringNotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val KEY_TEMPLATE_ID = "templateId"

        /** Канал с высокой важностью: уведомление всплывает поверх работающего экрана. */
        const val CHANNEL_ID = "recurring_alerts"

        /** Прежний канал с обычной важностью: важность существующего канала менять нельзя, поэтому он заменён. */
        private const val LEGACY_CHANNEL_ID = "recurring"
        private const val NOTIFICATION_ID_BASE = 2000
        private const val KIND_OPEN = 0
        private const val KIND_CONFIRM = 1
        private const val KIND_EDIT = 2
        private const val KIND_CANCEL = 3
        private const val KINDS = 4
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

        fun notificationId(templateId: Long): Int = NOTIFICATION_ID_BASE + templateId.toInt()

        /** PendingIntent различаются только кодом запроса (extras не учитываются), поэтому код уникален на кнопку. */
        private fun requestCode(templateId: Long, kind: Int): Int = templateId.toInt() * KINDS + kind

        /** Канал «Регулярные операции» (Android 8+). Безопасно вызывать повторно. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.recurring_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.recurring_channel_description) }
            manager.createNotificationChannel(channel)
        }
    }
}
