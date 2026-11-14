package app.fingramm.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 → 2: регулярные операции получили расписание «по дням недели» и время уведомления.
 * Все прежние шаблоны остаются ежемесячными и без уведомления, то есть ведут себя как раньше.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recurring_template ADD COLUMN frequency TEXT NOT NULL DEFAULT 'MONTHLY'")
        db.execSQL("ALTER TABLE recurring_template ADD COLUMN weekdaysMask INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE recurring_template ADD COLUMN notifyAtMinute INTEGER")
    }
}

/**
 * 2 → 3: из приложения убран опрос и плановые расчёты, поэтому его таблицы (ответы опроса, источники дохода,
 * активы, долги) больше не нужны. Профиль с основной валютой, операции, категории и цели остаются нетронутыми.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS survey_answers")
        db.execSQL("DROP TABLE IF EXISTS income_source")
        db.execSQL("DROP TABLE IF EXISTS asset")
        db.execSQL("DROP TABLE IF EXISTS debt")
    }
}

/**
 * 3 → 4: категории получили признак «перевод между своими счетами» (в итоги не входит). У всех прежних категорий
 * он выключен, поэтому итоги и остаток не меняются.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE category ADD COLUMN isTransfer INTEGER NOT NULL DEFAULT 0")
    }
}
