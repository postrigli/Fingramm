package app.fingramm.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
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
interface RecurringActionsEntryPoint {
    fun occurrenceHandler(): RecurringOccurrenceHandler
}

/**
 * Кнопки «Подтвердить» и «Отменить» в уведомлении о регулярной операции. Приёмник не экспортируется: запустить его
 * может только само уведомление. Закрывает уведомление после действия.
 */
class RecurringNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, 0)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (templateId <= 0 || intent.action !in setOf(ACTION_CONFIRM, ACTION_CANCEL)) return
        // Уведомление убираем сразу: так нажатие видно, даже если запись в БД займёт мгновение.
        NotificationManagerCompat.from(context).cancel(notificationId)
        val pending = goAsync()
        val action = intent.action
        val app = context.applicationContext
        scope.launch {
            try {
                val handler = EntryPointAccessors.fromApplication(app, RecurringActionsEntryPoint::class.java)
                    .occurrenceHandler()
                if (action == ACTION_CONFIRM) handler.confirm(templateId) else handler.skip(templateId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM = "app.fingramm.action.RECURRING_CONFIRM"
        const val ACTION_CANCEL = "app.fingramm.action.RECURRING_CANCEL"
        const val EXTRA_TEMPLATE_ID = "app.fingramm.extra.RECURRING_TEMPLATE_ID"
        const val EXTRA_NOTIFICATION_ID = "app.fingramm.extra.RECURRING_NOTIFICATION_ID"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
