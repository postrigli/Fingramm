package app.fingramm.domain.model

import app.fingramm.domain.backup.LocalDateIsoSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Единственный профиль бюджета. Его наличие означает, что онбординг пройден. */
@Serializable
public data class BudgetProfile(
    val familySize: Int,
    val hasChildren: Boolean,
    val childrenCount: Int,
    val currency: CurrencyCode,
    @Serializable(with = LocalDateIsoSerializer::class) val createdAt: LocalDate,
)

@Serializable
public data class NamedAmount(val name: String, val amountMinor: Long)

@Serializable
public data class SavingsEntry(val place: SavingsPlace, val amountMinor: Long)

/** Ответы опроса, не вынесенные в отдельные таблицы. Все суммы — в минимальных единицах валюты. */
@Serializable
public data class SurveyAnswers(
    val mandatoryMode: InputMode = InputMode.TOTAL,
    val mandatoryTotalMinor: Long = 0,
    val mandatoryItems: List<NamedAmount> = emptyList(),
    val variableMode: InputMode = InputMode.TOTAL,
    val variableTotalMinor: Long = 0,
    val variableItems: List<NamedAmount> = emptyList(),
    /** Пользователь не знает переменные расходы — оценим по фактическим операциям. */
    val variableUnknown: Boolean = false,
    val hasCushion: Boolean = false,
    val cushionMinor: Long = 0,
    val savings: List<SavingsEntry> = emptyList(),
    val includeAssetsInCapital: Boolean = false,
    val experience: BookkeepingExperience? = null,
    val timeCommitment: TimeCommitment? = null,
) {
    public val mandatoryMonthlyMinor: Long
        get() = if (mandatoryMode == InputMode.TOTAL) mandatoryTotalMinor else mandatoryItems.sumOf { it.amountMinor }

    /** Заявленные переменные расходы; 0, если пользователь выбрал «не знаю». */
    public val variableDeclaredMinor: Long
        get() = when {
            variableUnknown -> 0L
            variableMode == InputMode.TOTAL -> variableTotalMinor
            else -> variableItems.sumOf { it.amountMinor }
        }

    public val savingsTotalMinor: Long get() = savings.sumOf { it.amountMinor }
}

@Serializable
public data class IncomeSource(val id: Long = 0, val name: String, val amountMinor: Long)

@Serializable
public data class Asset(val id: Long = 0, val name: String, val valueMinor: Long)

@Serializable
public data class Debt(
    val id: Long = 0,
    val name: String,
    val monthlyPaymentMinor: Long,
    val balanceMinor: Long? = null,
)

@Serializable
public data class Category(
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val kind: CategoryKind,
    /** Эмодзи-иконка категории. */
    val icon: String,
    val archived: Boolean = false,
    /** Платежи по кредитам: попадают в «платежи по долгам», а не в обязательные расходы. */
    val isDebtPayment: Boolean = false,
)

@Serializable
public data class Transaction(
    val id: Long = 0,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    @Serializable(with = LocalDateIsoSerializer::class) val date: LocalDate,
    val note: String = "",
    val accountType: AccountType? = null,
    /** Зарезервировано под участников семьи, в v1 UI нет. */
    val memberId: Long? = null,
    val recurringId: Long? = null,
    /** Когда операция была внесена (epoch millis); нужно для умных напоминаний. */
    val createdAt: Long = 0,
)

@Serializable
public data class RecurringTemplate(
    val id: Long = 0,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val note: String = "",
    val accountType: AccountType? = null,
    /** День месяца 1–31 для [RecurrenceFrequency.MONTHLY]; при еженедельном повторе не используется. */
    val dayOfMonth: Int,
    /** Ближайшая ещё не обработанная дата списания. */
    @Serializable(with = LocalDateIsoSerializer::class) val nextDueDate: LocalDate,
    val active: Boolean = true,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    /** Дни недели по ISO (1 — понедельник … 7 — воскресенье) для [RecurrenceFrequency.WEEKLY]. */
    val weekdays: Set<Int> = emptySet(),
    /** Время уведомления в минутах от полуночи (0–1439); null — без уведомления. */
    val notifyAtMinute: Int? = null,
)

@Serializable
public data class Goal(
    val id: Long = 0,
    val title: String,
    val targetMinor: Long? = null,
    val savedMinor: Long = 0,
    @Serializable(with = LocalDateIsoSerializer::class) val deadline: LocalDate? = null,
    val horizon: GoalHorizon,
    @Serializable(with = LocalDateIsoSerializer::class) val createdAt: LocalDate,
)

@Serializable
public data class CategoryLimit(val categoryId: Long, val limitMinor: Long)

/** Настройки напоминаний. [weekday]: 1 = понедельник … 7 = воскресенье (ISO). */
@Serializable
public data class ReminderSettings(
    val mode: ReminderMode = ReminderMode.OFF,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = DEFAULT_MINUTE,
    val weekday: Int = DEFAULT_WEEKDAY,
) {
    public companion object {
        public const val DEFAULT_HOUR: Int = 20
        public const val DEFAULT_MINUTE: Int = 30
        public const val DEFAULT_WEEKDAY: Int = 7
    }
}
