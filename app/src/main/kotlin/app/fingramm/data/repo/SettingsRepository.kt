package app.fingramm.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.ReminderSettings
import app.fingramm.domain.period.BudgetPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

const val NO_QUIET_DAY: Long = -1L

/** Настройки приложения, не относящиеся к бюджету (DataStore). */
data class AppSettings(
    val reminder: ReminderSettings = ReminderSettings(),
    val disclaimerShown: Boolean = false,
    val lockEnabled: Boolean = false,
    /** День начала расчётного месяца 1–28; 1 — календарный месяц. Спрашивать при первом запуске не нужно. */
    val monthStartDay: Int = BudgetPeriod.DEFAULT_START_DAY,
    /** Версия набора предустановленных категорий, уже добавленных в приложение. */
    val presetsVersion: Int = 1,
    /** День (epoch day), когда пользователь нажал «Ничего не тратил»: вечернее напоминание в этот день не приходит. */
    val quietDay: Long = NO_QUIET_DAY,
    /** Остаток средств на начало учёта (может быть отрицательным). */
    val openingBalanceMinor: Long = 0,
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
            monthStartDay = BudgetPeriod.normalize(prefs[MONTH_START_DAY] ?: BudgetPeriod.DEFAULT_START_DAY),
            presetsVersion = prefs[PRESETS_VERSION] ?: 1,
            quietDay = prefs[QUIET_DAY] ?: NO_QUIET_DAY,
            openingBalanceMinor = prefs[OPENING_BALANCE] ?: 0L,
        )
    }

    /** Только день начала месяца: репозитории пересчитывают итоги, лишь когда он действительно изменился. */
    val monthStartDay: Flow<Int> = settings.map { it.monthStartDay }.distinctUntilChanged()

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

    suspend fun setMonthStartDay(day: Int) {
        dataStore.edit { it[MONTH_START_DAY] = BudgetPeriod.normalize(day) }
    }

    suspend fun setOpeningBalance(minor: Long) {
        dataStore.edit { it[OPENING_BALANCE] = minor }
    }

    suspend fun setPresetsVersion(version: Int) {
        dataStore.edit { it[PRESETS_VERSION] = version }
    }

    /** Пользователь сообщил, что сегодня трат не было: до конца этого дня вечернее напоминание молчит. */
    suspend fun setQuietDay(day: LocalDate) {
        dataStore.edit { it[QUIET_DAY] = day.toEpochDay() }
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
        val MONTH_START_DAY = intPreferencesKey("month_start_day")
        val PRESETS_VERSION = intPreferencesKey("presets_version")
        val QUIET_DAY = longPreferencesKey("quiet_day")
        val OPENING_BALANCE = longPreferencesKey("opening_balance")
    }
}
