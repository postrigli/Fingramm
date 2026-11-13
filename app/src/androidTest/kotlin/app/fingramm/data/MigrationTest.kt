package app.fingramm.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fingramm.data.db.AppDatabase
import app.fingramm.data.db.MIGRATION_1_2
import app.fingramm.data.db.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверка миграций Room. Файлы схем в каталоге `app/schemas` создаются при сборке и должны быть закоммичены:
 * тест открывает каждую старую версию и мигрирует её до текущей. При добавлении новой версии схемы тест
 * автоматически потребует миграцию и файл схемы.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun currentSchemaMatchesExportedJson() {
        helper.createDatabase(DB_NAME, AppDatabase.VERSION).close()
        helper.runMigrationsAndValidate(DB_NAME, AppDatabase.VERSION, true, *AppDatabase.MIGRATIONS).close()
    }

    @Test
    fun everyOlderVersionMigratesToTheCurrentOne() {
        for (version in 1 until AppDatabase.VERSION) {
            helper.createDatabase(DB_NAME, version).close()
            helper.runMigrationsAndValidate(DB_NAME, AppDatabase.VERSION, true, *AppDatabase.MIGRATIONS).close()
        }
    }

    @Test
    fun migration1To2KeepsRecurringTemplatesMonthlyWithoutNotification() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO category (id, name, type, kind, icon, archived, isDebtPayment) " +
                    "VALUES (1, 'Аренда', 'EXPENSE', 'MANDATORY', 'H', 0, 0)",
            )
            execSQL(
                "INSERT INTO recurring_template " +
                    "(id, type, amountMinor, categoryId, note, accountType, dayOfMonth, nextDueDate, active) " +
                    "VALUES (7, 'EXPENSE', 3000000, 1, 'Аренда', NULL, 15, '2026-10-15', 1)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)
        db.query(
            "SELECT frequency, weekdaysMask, notifyAtMinute, dayOfMonth, nextDueDate, active, amountMinor " +
                "FROM recurring_template WHERE id = 7",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("MONTHLY", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertTrue(cursor.isNull(2))
            assertEquals(15, cursor.getInt(3))
            assertEquals("2026-10-15", cursor.getString(4))
            assertEquals(1, cursor.getInt(5))
            assertEquals(3_000_000L, cursor.getLong(6))
        }
        db.close()
    }

    @Test
    fun migration2To3DropsSurveyTablesAndKeepsEverythingElse() {
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL(
                "INSERT INTO budget_profile (id, familySize, hasChildren, childrenCount, currency, createdAt) " +
                    "VALUES (1, 3, 1, 1, 'EUR', '2026-09-01')",
            )
            execSQL("INSERT INTO income_source (id, name, amountMinor) VALUES (1, 'Зарплата', 10000000)")
            execSQL("INSERT INTO asset (id, name, valueMinor) VALUES (1, 'Авто', 50000000)")
            execSQL(
                "INSERT INTO category (id, name, type, kind, icon, archived, isDebtPayment) " +
                    "VALUES (1, 'Продукты', 'EXPENSE', 'MANDATORY', 'C', 0, 0)",
            )
            execSQL(
                "INSERT INTO transaction_entry (id, type, amountMinor, categoryId, date, note, noteLower, " +
                    "accountType, memberId, recurringId, createdAt) " +
                    "VALUES (5, 'EXPENSE', 12300, 1, '2026-10-05', 'Хлеб', 'хлеб', NULL, NULL, NULL, 1)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3)
        db.query("SELECT currency FROM budget_profile WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("EUR", cursor.getString(0))
        }
        db.query("SELECT amountMinor, note FROM transaction_entry WHERE id = 5").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(12_300L, cursor.getLong(0))
            assertEquals("Хлеб", cursor.getString(1))
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name IN ('survey_answers', 'income_source', 'asset', 'debt')",
        ).use { cursor -> assertEquals(0, cursor.count) }
        db.close()
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
