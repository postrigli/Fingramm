package app.fingramm.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fingramm.data.db.AppDatabase
import app.fingramm.data.db.MIGRATION_1_2
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

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
