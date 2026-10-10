package app.fingramm.data.db

import androidx.room.TypeConverter
import app.fingramm.domain.model.NamedAmount
import app.fingramm.domain.model.SavingsEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Даты хранятся ISO-строкой: так по ним работают диапазоны и группировка `substr(date, 1, 7)`. */
class Converters {
    @TypeConverter
    fun dateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun namedAmountsToString(value: List<NamedAmount>): String =
        json.encodeToString(ListSerializer(NamedAmount.serializer()), value)

    @TypeConverter
    fun stringToNamedAmounts(value: String): List<NamedAmount> =
        json.decodeFromString(ListSerializer(NamedAmount.serializer()), value)

    @TypeConverter
    fun savingsToString(value: List<SavingsEntry>): String =
        json.encodeToString(ListSerializer(SavingsEntry.serializer()), value)

    @TypeConverter
    fun stringToSavings(value: String): List<SavingsEntry> =
        json.decodeFromString(ListSerializer(SavingsEntry.serializer()), value)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
