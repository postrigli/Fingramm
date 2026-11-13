package app.fingramm.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.fingramm.data.repo.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuietDayEntryPoint {
    fun settings(): SettingsRepository

    fun clock(): Clock
}

/**
 * Кнопка «Ничего не тратил» в вечернем напоминании: убирает уведомление и запоминает, что сегодня напоминать
 * больше не нужно. Приёмник не экспортируется: запустить его может только само уведомление.
 */
class QuietDayReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_NOTHING_SPENT) return
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                val entry = EntryPointAccessors.fromApplication(app, QuietDayEntryPoint::class.java)
                entry.settings().setQuietDay(LocalDate.now(entry.clock()))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_NOTHING_SPENT = "app.fingramm.action.NOTHING_SPENT"
        const val EXTRA_NOTIFICATION_ID = "app.fingramm.extra.QUIET_NOTIFICATION_ID"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
