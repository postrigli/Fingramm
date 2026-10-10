package app.fingramm.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        BudgetProfileEntity::class,
        SurveyAnswersEntity::class,
        IncomeSourceEntity::class,
        AssetEntity::class,
        DebtEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringTemplateEntity::class,
        GoalEntity::class,
        CategoryLimitEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun recurringDao(): RecurringDao

    abstract fun goalDao(): GoalDao

    companion object {
        const val VERSION = 2
        const val NAME = "fingramm.db"

        /** Миграции между версиями схемы. Для каждой — тест в androidTest/MigrationTest. */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf(MIGRATION_1_2)
    }
}
