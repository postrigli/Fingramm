package app.fingramm.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.money.Money
import app.fingramm.domain.period.BudgetPeriod
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

/**
 * Подпись расчётного месяца. Календарный (день начала 1) — «октябрь 2026»; со сдвигом названия мало, поэтому
 * показываем даты периода: «25 окт. – 24 нояб. 2026». Год пишется у конца периода (и у начала, если годы разные).
 */
fun formatPeriod(month: YearMonth, startDay: Int): String {
    if (startDay <= BudgetPeriod.DEFAULT_START_DAY) return formatMonth(month)
    val start = BudgetPeriod.start(month, startDay)
    val end = BudgetPeriod.end(month, startDay)
    val from = if (start.year == end.year) shortDayMonth(start) else "${shortDayMonth(start)} ${start.year}"
    return "$from – ${shortDayMonth(end)} ${end.year}"
}

private fun shortDayMonth(date: LocalDate): String =
    "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, ru)}"

fun formatMonthShort(month: YearMonth): String = month.month.getDisplayName(TextStyle.SHORT_STANDALONE, ru)

/** «10 октября», с годом, если он не текущий. */
fun formatDate(date: LocalDate, today: LocalDate): String {
    val base = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, ru)}"
    return if (date.year == today.year) base else "$base ${date.year}"
}

fun formatDateFull(date: LocalDate): String =
    "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, ru)} ${date.year}"

fun formatWeekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, ru)

/** Короткое название дня недели заглавными: «ПН», «ВТ», … «ВС». */
fun weekdayShort(isoDay: Int): String =
    DayOfWeek.of(isoDay).getDisplayName(TextStyle.SHORT_STANDALONE, ru).uppercase(ru)

fun weekdayName(isoDay: Int): String = DayOfWeek.of(isoDay).getDisplayName(TextStyle.FULL_STANDALONE, ru)

fun formatTime(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

/** Число с одной цифрой после запятой, «,0» опускается: 3,8. */
fun formatDecimal(value: Double): String {
    val rounded = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP)
    return rounded.toPlainString().removeSuffix(".0").replace('.', ',')
}
