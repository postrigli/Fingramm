package app.fingramm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.fingramm.domain.model.AccountType
import app.fingramm.domain.model.BookkeepingExperience
import app.fingramm.domain.model.CategoryKind
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.GoalHorizon
import app.fingramm.domain.model.InputMode
import app.fingramm.domain.model.NamedAmount
import app.fingramm.domain.model.RecurrenceFrequency
import app.fingramm.domain.model.SavingsEntry
import app.fingramm.domain.model.TimeCommitment
import app.fingramm.domain.model.TransactionType
import java.time.LocalDate

/** Таблицы singleton-строк используют фиксированный id. */
const val SINGLETON_ID = 1L

@Entity(tableName = "budget_profile")
data class BudgetProfileEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val familySize: Int,
    val hasChildren: Boolean,
    val childrenCount: Int,
    val currency: CurrencyCode,
    val createdAt: LocalDate,
)

@Entity(tableName = "survey_answers")
data class SurveyAnswersEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val mandatoryMode: InputMode,
    val mandatoryTotalMinor: Long,
    val mandatoryItems: List<NamedAmount>,
    val variableMode: InputMode,
    val variableTotalMinor: Long,
    val variableItems: List<NamedAmount>,
    val variableUnknown: Boolean,
    val hasCushion: Boolean,
    val cushionMinor: Long,
    val savings: List<SavingsEntry>,
    val includeAssetsInCapital: Boolean,
    val experience: BookkeepingExperience?,
    val timeCommitment: TimeCommitment?,
)

@Entity(tableName = "income_source")
data class IncomeSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountMinor: Long,
)

@Entity(tableName = "asset")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val valueMinor: Long,
)

@Entity(tableName = "debt")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val monthlyPaymentMinor: Long,
    val balanceMinor: Long?,
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val kind: CategoryKind,
    val icon: String,
    val archived: Boolean,
    val isDebtPayment: Boolean,
)

@Entity(
    tableName = "recurring_template",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId")],
)
data class RecurringTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val note: String,
    val accountType: AccountType?,
    val dayOfMonth: Int,
    val nextDueDate: LocalDate,
    val active: Boolean,
    /** Добавлено в версии 2 схемы. */
    val frequency: RecurrenceFrequency,
    /** Дни недели битовой маской: бит 0 — понедельник … бит 6 — воскресенье. */
    val weekdaysMask: Int,
    /** Время уведомления в минутах от полуночи; null — без уведомления. */
    val notifyAtMinute: Int?,
)

@Entity(
    tableName = "transaction_entry",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RecurringTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("date"), Index("categoryId"), Index("recurringId"), Index("createdAt")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val date: LocalDate,
    val note: String,
    /** Комментарий в нижнем регистре: SQLite не умеет регистронезависимый LIKE для кириллицы. */
    val noteLower: String,
    val accountType: AccountType?,
    val memberId: Long?,
    val recurringId: Long?,
    val createdAt: Long,
)

@Entity(
    tableName = "goal",
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetMinor: Long?,
    val savedMinor: Long,
    val deadline: LocalDate?,
    val horizon: GoalHorizon,
    val createdAt: LocalDate,
)

@Entity(
    tableName = "category_limit",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CategoryLimitEntity(
    @PrimaryKey @ColumnInfo(name = "categoryId") val categoryId: Long,
    val limitMinor: Long,
)
