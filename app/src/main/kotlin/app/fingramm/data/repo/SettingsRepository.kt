package app.fingramm.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Настройки приложения, не относящиеся к бюджету (DataStore). */
data class AppSettings(
    val reminder: ReminderSettings = ReminderSettings(),
    val disclaimerShown: Boolean = false,
    val lockEnabled: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            reminder = ReminderSettings(
                mode = prefs[REMINDER_MODE]?.let { runCatching { ReminderMode.valueOf(it) }.getOrNull() }
                    ?: ReminderMode.OFF,
                hour = prefs[REMINDER_HOUR] ?: ReminderSettings.DEFAULT_HOUR,
                minute = prefs[REMINDER_MINUTE] ?: ReminderSettings.DEFAULT_MINUTE,
                weekday = prefs[REMINDER_WEEKDAY] ?: ReminderSettings.DEFAULT_WEEKDAY,
            ),
            disclaimerShown = prefs[DISCLAIMER_SHOWN] ?: false,
            lockEnabled = prefs[LOCK_ENABLED] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setReminder(reminder: ReminderSettings) {
        dataStore.edit {
            it[REMINDER_MODE] = reminder.mode.name
            it[REMINDER_HOUR] = reminder.hour
            it[REMINDER_MINUTE] = reminder.minute
            it[REMINDER_WEEKDAY] = reminder.weekday
        }
    }

    suspend fun setDisclaimerShown() {
        dataStore.edit { it[DISCLAIMER_SHOWN] = true }
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        dataStore.edit { it[LOCK_ENABLED] = enabled }
    }

    /** Полный сброс настроек (после «Удалить все данные»). */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val REMINDER_MODE = stringPreferencesKey("reminder_mode")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMINDER_WEEKDAY = intPreferencesKey("reminder_weekday")
        val DISCLAIMER_SHOWN = booleanPreferencesKey("disclaimer_shown")
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
    }
}
