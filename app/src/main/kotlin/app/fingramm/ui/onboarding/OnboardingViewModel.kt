package app.fingramm.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.data.repo.RecurringRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.DefaultCategories
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.ReminderSettings
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.period.BudgetPeriod
import app.fingramm.domain.recurring.Recurrence
import app.fingramm.domain.recurring.RecurringSchedule
import app.fingramm.notifications.RecurringReminderScheduler
import app.fingramm.notifications.ReminderScheduler
import app.fingramm.ui.CategoryPresets
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val MINUTES_PER_HOUR = 60
private const val DEFAULT_NOTIFY_MINUTE = 9 * MINUTES_PER_HOUR
private const val MAX_DAY_OF_MONTH = 31

/** Строка типового платежа: отмечен ли он и что введено. */
data class StarterRow(
    val payment: StarterPayment,
    val enabled: Boolean = false,
    val amountMinor: Long? = null,
    val day: Int? = payment.defaultDay,
) {
    val dayValid: Boolean get() = day != null && day in 1..MAX_DAY_OF_MONTH
    val amountValid: Boolean get() = (amountMinor ?: 0L) > 0L

    /** Неотмеченный платёж всегда допустим; отмеченному нужны сумма и день. */
    val valid: Boolean get() = !enabled || (amountValid && dayValid)
}

data class OnboardingUiState(
    val currency: CurrencyCode = CurrencyCode.RUB,
    /** Сколько денег у пользователя на начало учёта (со знаком); null — не введено, считается нулём. */
    val balanceMinor: Long? = null,
    val monthStartDay: Int = BudgetPeriod.DEFAULT_START_DAY,
    /** Основной доход или зарплата. */
    val primaryIncome: IncomeRow = IncomeRow(),
    /** Второй доход (вторая работа, аренда); null — не добавлен. */
    val secondIncome: IncomeRow? = null,
    val rows: List<StarterRow> = StarterPayments.all.map { StarterRow(it) },
    /** Время уведомлений о регулярных платежах и доходах (минуты от полуночи), одно на все. */
    val notifyMinute: Int = DEFAULT_NOTIFY_MINUTE,
    val eveningReminder: Boolean = true,
    val eveningMinute: Int = ReminderSettings.DEFAULT_HOUR * MINUTES_PER_HOUR + ReminderSettings.DEFAULT_MINUTE,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
) {
    /** Доходы, которые нужно создать. */
    val incomes: List<IncomeRow> get() = listOfNotNull(primaryIncome, secondIncome).filter { it.enabled }

    val anyRecurring: Boolean get() = rows.any { it.enabled } || incomes.isNotEmpty()
    val valid: Boolean get() = rows.all { it.valid } && primaryIncome.valid && (secondIncome?.valid ?: true)

    /** Нужно ли разрешение на уведомления: есть что присылать. */
    val needsNotifications: Boolean get() = eveningReminder || anyRecurring
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val recurring: RecurringRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val recurringReminders: RecurringReminderScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _events = Channel<Unit>(Channel.BUFFERED)

    /** Срабатывает один раз, когда всё сохранено и можно открывать сводку. */
    val finished = _events.receiveAsFlow()

    fun update(transform: (OnboardingUiState) -> OnboardingUiState) = _state.update(transform)

    fun start() {
        val current = _state.value
        if (current.saving) return
        if (!current.valid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            budgetRepository.completeOnboarding(current.currency, today, CategoryPresets.build(context))
            settings.setMonthStartDay(current.monthStartDay)
            settings.setOpeningBalance(current.balanceMinor ?: 0L)
            settings.setPresetsVersion(DefaultCategories.CURRENT_VERSION)
            createRecurring(current, today)
            saveEveningReminder(current)
            recurringReminders.syncAll()
            _events.send(Unit)
        }
    }

    private suspend fun createRecurring(state: OnboardingUiState, today: LocalDate) {
        val categories = categoryRepository.categories.first()
        state.rows.filter { it.enabled }.forEach { row ->
            val day = row.day ?: return@forEach
            val amount = row.amountMinor ?: return@forEach
            saveTemplate(
                categories,
                row.payment.categoryKey,
                row.payment.type,
                amount,
                Recurrence.monthly(day),
                state.notifyMinute,
                today,
            )
        }
        state.incomes.forEach { income ->
            val amount = income.amountMinor ?: return@forEach
            income.recurrences().forEach { rule ->
                saveTemplate(
                    categories,
                    income.kind.categoryKey,
                    TransactionType.INCOME,
                    amount,
                    rule,
                    state.notifyMinute,
                    today,
                )
            }
        }
    }

    @Suppress("LongParameterList")
    private suspend fun saveTemplate(
        categories: List<Category>,
        categoryKey: String,
        type: TransactionType,
        amountMinor: Long,
        rule: Recurrence,
        notifyMinute: Int,
        today: LocalDate,
    ) {
        val name = CategoryPresets.name(context, categoryKey)
        val category = categories.firstOrNull { it.name == name && it.type == type } ?: return
        recurring.save(
            RecurringTemplate(
                type = type,
                amountMinor = amountMinor,
                categoryId = category.id,
                dayOfMonth = rule.dayOfMonth,
                nextDueDate = RecurringSchedule.firstDueOnOrAfter(today, rule),
                frequency = rule.frequency,
                weekdays = rule.weekdays,
                notifyAtMinute = notifyMinute,
            ),
        )
    }

    private suspend fun saveEveningReminder(state: OnboardingUiState) {
        val reminder = if (state.eveningReminder) {
            ReminderSettings(
                mode = ReminderMode.DAILY_EVENING,
                hour = state.eveningMinute / MINUTES_PER_HOUR,
                minute = state.eveningMinute % MINUTES_PER_HOUR,
            )
        } else {
            ReminderSettings(mode = ReminderMode.OFF)
        }
        settings.setReminder(reminder)
        scheduler.apply(reminder)
    }
}
