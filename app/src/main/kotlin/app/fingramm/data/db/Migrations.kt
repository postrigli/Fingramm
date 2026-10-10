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
