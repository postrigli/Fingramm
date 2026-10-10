package app.fingramm.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fingramm.data.db.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверка миграций Room. Файлы схем в каталоге `app/schemas` создаются при сборке и должны быть закоммичены:
 * тест открывает каждую старую версию и мигрирует её до текущей. Пока версия одна, проверяется
 * соответствие реальной схемы файлу; при добавлении версии 2 тест автоматически потребует миграцию 1→2.
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

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
