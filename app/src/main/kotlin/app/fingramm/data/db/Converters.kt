package app.fingramm.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

/** Даты хранятся ISO-строкой: так по ним работают диапазоны и группировка `substr(date, 1, 7)`. */
class Converters {
    @TypeConverter
    fun dateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
