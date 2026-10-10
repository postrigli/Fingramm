package app.fingramm.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Валюта бюджета для форматирования сумм во всём дереве UI. */
val LocalCurrency = staticCompositionLocalOf { CurrencyCode.RUB }

private val ru: Locale = Locale.forLanguageTag("ru")

@Composable
fun rememberMoneyFormatter(): (Long) -> String {
    val currency = LocalCurrency.current
    return { minor -> Money.format(minor, currency) }
}

/** Месяц словами: «октябрь 2026». */
fun formatMonth(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, ru)} ${month.year}"

fun formatMonthShort(month: YearMonth): String = month.month.getDisplayName(TextStyle.SHORT_STANDALONE, ru)

/** «10 октября», с годом, если он не текущий. */
fun formatDate(date: LocalDate, today: LocalDate): String {
    val base = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, ru)}"
    return if (date.year == today.year) base else "$base ${date.year}"
}

fun formatDateFull(date: LocalDate): String =
    "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, ru)} ${date.year}"

fun formatWeekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, ru)

fun weekdayName(isoDay: Int): String = DayOfWeek.of(isoDay).getDisplayName(TextStyle.FULL_STANDALONE, ru)

fun formatTime(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

/** Число с одной цифрой после запятой, «,0» опускается: 3,8. */
fun formatDecimal(value: Double): String {
    val rounded = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP)
    return rounded.toPlainString().removeSuffix(".0").replace('.', ',')
}
