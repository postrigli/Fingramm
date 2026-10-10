package app.fingramm.data.repo

import androidx.room.withTransaction
import app.fingramm.data.db.AppDatabase
import app.fingramm.data.toDomain
import app.fingramm.data.toEntity
import app.fingramm.domain.backup.BackupData
import app.fingramm.domain.backup.BackupMerger
import javax.inject.Inject
import javax.inject.Singleton

enum class ImportMode { REPLACE, MERGE }

/** Снимок и восстановление всех таблиц; настройки напоминаний хранятся в DataStore и обрабатываются отдельно. */
@Singleton
class BackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    suspend fun snapshot(): BackupData = db.withTransaction {
        val profileDao = db.profileDao()
        BackupData(
            profile = profileDao.getProfile()?.toDomain(),
            survey = profileDao.getSurvey()?.toDomain(),
            incomeSources = profileDao.getIncomeSources().map { it.toDomain() },
            assets = profileDao.getAssets().map { it.toDomain() },
            debts = profileDao.getDebts().map { it.toDomain() },
            categories = db.categoryDao().getAll().map { it.toDomain() },
            transactions = db.transactionDao().getAll().map { it.toDomain() },
            recurringTemplates = db.recurringDao().getAll().map { it.toDomain() },
            goals = db.goalDao().getAll().map { it.toDomain() },
            categoryLimits = db.categoryDao().getLimits().map { it.toDomain() },
            reminder = settings.current().reminder,
        )
    }

    /** Применяет импорт. При слиянии результат считается чистой функцией домена и записывается как замена. */
    suspend fun importData(incoming: BackupData, mode: ImportMode): BackupData {
        val result = when (mode) {
            ImportMode.REPLACE -> incoming
            ImportMode.MERGE -> BackupMerger.merge(snapshot(), incoming)
        }
        replaceAll(result)
        // При слиянии `result.reminder` — текущие настройки (или импортированные, если профиля ещё не было).
        settings.setReminder(result.reminder)
        return result
    }

    /** Удаляет все данные приложения, включая настройки. */
    suspend fun wipe() {
        replaceAll(BackupData())
        settings.clear()
    }

    private suspend fun replaceAll(data: BackupData) = db.withTransaction {
        // Порядок учитывает внешние ключи: сначала дочерние таблицы.
        db.transactionDao().clear()
        db.recurringDao().clear()
        db.categoryDao().clearLimits()
        db.goalDao().clear()
        db.categoryDao().clear()
        val profileDao = db.profileDao()
        profileDao.clearIncomeSources()
        profileDao.clearAssets()
        profileDao.clearDebts()
        profileDao.clearSurvey()
        profileDao.clearProfile()

        data.profile?.let { profileDao.upsertProfile(it.toEntity()) }
        data.survey?.let { profileDao.upsertSurvey(it.toEntity()) }
        profileDao.insertIncomeSources(data.incomeSources.map { it.toEntity() })
        profileDao.insertAssets(data.assets.map { it.toEntity() })
        profileDao.insertDebts(data.debts.map { it.toEntity() })
        db.categoryDao().insertAll(data.categories.map { it.toEntity() })
        db.recurringDao().insertAll(data.recurringTemplates.map { it.toEntity() })
        db.transactionDao().insertAll(data.transactions.map { it.toEntity() })
        db.categoryDao().insertLimits(data.categoryLimits.map { it.toEntity() })
        db.goalDao().insertAll(data.goals.map { it.toEntity() })
    }
}
