package app.fingramm.notifications

import app.fingramm.data.repo.CategoryPresetInstaller
import app.fingramm.data.repo.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Приводит расписания уведомлений и набор категорий в соответствие с данными после импорта резервной копии. */
@Singleton
class ScheduleSync @Inject constructor(
    private val settings: SettingsRepository,
    private val reminders: ReminderScheduler,
    private val recurringReminders: RecurringReminderScheduler,
    private val monthReports: MonthReportScheduler,
    private val presets: CategoryPresetInstaller,
) {
    suspend fun afterImport() {
        reminders.apply(settings.current().reminder)
        recurringReminders.syncAll()
        monthReports.schedule()
        presets.sync()
    }
}
