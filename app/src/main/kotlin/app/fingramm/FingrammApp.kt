package app.fingramm

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.notifications.RecurringReminderScheduler
import app.fingramm.notifications.RecurringReminderWorker
import app.fingramm.notifications.ReminderScheduler
import app.fingramm.notifications.ReminderWorker
import app.fingramm.widget.WidgetRefresher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FingrammApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var reminderScheduler: ReminderScheduler

    @Inject lateinit var recurringReminders: RecurringReminderScheduler

    @Inject lateinit var widgetRefresher: WidgetRefresher

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ReminderWorker.ensureChannel(this)
        RecurringReminderWorker.ensureChannel(this)
        // Пересчитываем ближайшее срабатывание при каждом запуске: подхватывает смену часового пояса и перевод часов.
        appScope.launch { reminderScheduler.apply(settings.current().reminder) }
        // То же для уведомлений о регулярных операциях: пересчёт при каждом запуске подхватывает смену пояса и времени.
        appScope.launch { recurringReminders.syncAll() }
        widgetRefresher.start(appScope)
    }
}
