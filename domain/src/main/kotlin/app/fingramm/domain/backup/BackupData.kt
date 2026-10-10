package app.fingramm.domain.backup

import app.fingramm.domain.model.Asset
import app.fingramm.domain.model.BudgetProfile
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryLimit
import app.fingramm.domain.model.Debt
import app.fingramm.domain.model.Goal
import app.fingramm.domain.model.IncomeSource
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.ReminderSettings
import app.fingramm.domain.model.SurveyAnswers
import app.fingramm.domain.model.Transaction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

/** Текущая версия схемы данных бэкапа. Увеличивать при несовместимых изменениях формата. */
public const val BACKUP_SCHEMA_VERSION: Int = 1
public const val BACKUP_APP_ID: String = "fingramm"

/** Полный снимок данных приложения — содержимое всех таблиц и настройки напоминаний. */
@Serializable
public data class BackupData(
    val profile: BudgetProfile? = null,
    val survey: SurveyAnswers? = null,
    val incomeSources: List<IncomeSource> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val recurringTemplates: List<RecurringTemplate> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val categoryLimits: List<CategoryLimit> = emptyList(),
    val reminder: ReminderSettings = ReminderSettings(),
)

/** Файл бэкапа: версия схемы + данные. */
@Serializable
public data class BackupFile(
    val app: String = BACKUP_APP_ID,
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    /** ISO-8601 момент экспорта (информативно). */
    val exportedAt: String = "",
    val data: BackupData,
)

/** Даты в бэкапе — ISO-8601 (`2026-10-10`), читаемо и не зависит от часового пояса. */
public object LocalDateIsoSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
