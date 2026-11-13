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
import app.fingramm.MainActivity
import app.fingramm.R
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.data.repo.TransactionRepository
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.TransactionType
import app.fingramm.widget.QuickAction
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate

/**
 * Показывает мягкое напоминание, если сегодня ещё не вносились операции, и планирует следующее.
 * Тексты без давления; без разрешения на уведомления приложение продолжает работать молча.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val transactions: TransactionRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = settingsRepository.current().reminder
        if (settings.mode == ReminderMode.OFF) return Result.success()

        val today = LocalDate.now(clock)
        val startOfToday = today.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val alreadyLogged = transactions.countCreatedSince(startOfToday) > 0
        // «Ничего не тратил» отключает напоминание до конца этого дня.
        val quiet = settingsRepository.current().quietDay == today.toEpochDay()
        if (!alreadyLogged && !quiet && canNotify()) {
            val text = when (settings.mode) {
                ReminderMode.WEEKLY -> R.string.notification_text_weekly
                else -> R.string.notification_text_daily
            }
            show(text)
        }
        scheduler.apply(settings)
        return Result.success()
    }

    private fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @android.annotation.SuppressLint("MissingPermission") // разрешение проверено в canNotify()
    private fun show(textRes: Int) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(
                context,
                MainActivity::class.java,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val addExpense = PendingIntent.getActivity(
            context,
            REQUEST_ADD_EXPENSE,
            QuickAction.launchIntent(context, QuickAction.AddTransaction(TransactionType.EXPENSE), NOTIFICATION_ID),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val nothingSpent = PendingIntent.getBroadcast(
            context,
            REQUEST_NOTHING_SPENT,
            Intent(context, QuietDayReceiver::class.java)
                .setAction(QuietDayReceiver.ACTION_NOTHING_SPENT)
                .putExtra(QuietDayReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(textRes))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(textRes)))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.reminder_action_add_expense), addExpense)
            .addAction(0, context.getString(R.string.reminder_action_nothing_spent), nothingSpent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "reminders"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_ADD_EXPENSE = 1002
        private const val REQUEST_NOTHING_SPENT = 1003

        /** Канал «Напоминания» (Android 8+). Безопасно вызывать повторно. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notification_channel_description) }
            manager.createNotificationChannel(channel)
        }
    }
}
