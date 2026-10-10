package app.fingramm.domain.money

import app.fingramm.domain.model.CurrencyCode
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Денежные вычисления без Double: суммы хранятся как Long в минимальных единицах (копейки/центы).
 *
 * Правила округления (едины для всего приложения):
 *  - доли и проценты считаются через [BigDecimal] с режимом HALF_UP;
 *  - «сколько месяцев/взносов нужно» округляется вверх (CEILING) — это консервативная оценка.
 */
public object Money {
    public const val MINOR_PER_MAJOR: Long = 100
    private const val MAX_MAJOR_DIGITS = 12
    private const val FRACTION_DIGITS = 2
    private const val GROUP_SIZE = 3

    /** Неразрывный пробел — разделитель тысяч и отступ перед символом валюты. */
    public const val NBSP: Char = ' '
    private const val MINUS: Char = '−'

    /** Целое число минимальных единиц → строка вида «1 234,56 ₽» (нулевые копейки опускаются). */
    public fun format(minor: Long, currency: CurrencyCode): String = "${formatPlain(minor)}$NBSP${currency.symbol}"

    /** То же без символа валюты: «−1 234,56». */
    public fun formatPlain(minor: Long): String {
        val negative = minor < 0
        val abs = BigInteger.valueOf(minor).abs()
        val (major, fraction) = abs.divideAndRemainder(BigInteger.valueOf(MINOR_PER_MAJOR))
        val sb = StringBuilder()
        if (negative) sb.append(MINUS)
        sb.append(group(major.toString()))
        if (fraction.signum() != 0) {
            sb.append(',').append(fraction.toString().padStart(FRACTION_DIGITS, '0'))
        }
        return sb.toString()
    }

    /**
     * Разбирает ввод пользователя («1 234,5», «1234.50», «  300 »). Возвращает null, если строка пустая,
     * содержит лишние символы, отрицательна, имеет более двух знаков после запятой или слишком велика.
     */
    public fun parse(text: String): Long? {
        val cleaned = text.filterNot { it.isWhitespace() || it == NBSP || it == ' ' }
        if (cleaned.isEmpty()) return null
        val separators = cleaned.count { it == ',' || it == '.' }
        if (separators > 1) return null
        val normalized = cleaned.replace(',', '.')
        val (majorPart, fractionPart) = if ('.' in normalized) {
            normalized.substringBefore('.') to normalized.substringAfter('.')
        } else {
            normalized to ""
        }
        val digitsOnly = majorPart.all { it in '0'..'9' } && fractionPart.all { it in '0'..'9' }
        val sizeOk = majorPart.length in 1..MAX_MAJOR_DIGITS && fractionPart.length <= FRACTION_DIGITS
        if (!digitsOnly || !sizeOk) return null
        val fraction = fractionPart.padEnd(FRACTION_DIGITS, '0').toLong()
        return majorPart.toLong() * MINOR_PER_MAJOR + fraction
    }

    /**
     * Нормализует текст в процессе набора: оставляет цифры и один десятичный разделитель (запятая),
     * не более двух знаков после запятой и группирует тысячи. Пустой ввод остаётся пустым.
     */
    public fun normalizeInput(text: String): String {
        val sb = StringBuilder()
        var separatorSeen = false
        var fractionLength = 0
        for (ch in text) {
            when {
                ch in '0'..'9' && !separatorSeen -> sb.append(ch)
                ch in '0'..'9' && fractionLength < FRACTION_DIGITS -> {
                    sb.append(ch)
                    fractionLength++
                }
                (ch == ',' || ch == '.') && !separatorSeen -> {
                    separatorSeen = true
                    sb.append(',')
                }
            }
        }
        val raw = sb.toString()
        val major = raw.substringBefore(',').trimStart('0').ifEmpty { if (raw.isEmpty()) "" else "0" }
            .take(MAX_MAJOR_DIGITS)
        val tail = if (separatorSeen) "," + raw.substringAfter(',') else ""
        return group(major) + tail
    }

    /** Доля `numerator / denominator` или null, если знаменатель не положителен. */
    public fun ratio(numerator: Long, denominator: Long): Double? {
        if (denominator <= 0) return null
        return BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * Сравнивает долю `numerator / denominator` с порогом в процентах без потери точности:
     * возвращает знак (numerator/denominator − percent/100). Знаменатель должен быть положительным.
     */
    public fun compareToPercent(numerator: Long, denominator: Long, percent: Int): Int {
        require(denominator > 0) { "denominator must be positive" }
        val left = BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(PERCENT_BASE))
        val right = BigInteger.valueOf(denominator).multiply(BigInteger.valueOf(percent.toLong()))
        return left.compareTo(right)
    }

    /** Деление с округлением вверх для неотрицательных чисел. */
    public fun ceilDiv(value: Long, divisor: Long): Long {
        require(divisor > 0) { "divisor must be positive" }
        require(value >= 0) { "value must not be negative" }
        return (value + divisor - 1) / divisor
    }

    /** Доля в виде процента: 0.1234 → «12,3 %» (одна цифра после запятой, «,0» опускается). */
    public fun formatPercent(fraction: Double): String {
        val percent = BigDecimal.valueOf(fraction).multiply(BigDecimal(PERCENT_BASE))
            .setScale(1, RoundingMode.HALF_UP)
        val text = percent.abs().toPlainString().removeSuffix(".0").replace('.', ',')
        val sign = if (percent.signum() < 0) MINUS.toString() else ""
        return "$sign$text$NBSP%"
    }

    private fun group(digits: String): String =
        digits.reversed().chunked(GROUP_SIZE).joinToString(NBSP.toString()).reversed()

    private const val RATIO_SCALE = 6
    private const val PERCENT_BASE = 100L
}
