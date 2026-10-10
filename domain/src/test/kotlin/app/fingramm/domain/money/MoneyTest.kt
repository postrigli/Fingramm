package app.fingramm.domain.money

import app.fingramm.domain.model.CurrencyCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    private val nb = Money.NBSP

    @Test
    fun `format groups thousands and drops zero minor part`() {
        assertEquals("1${nb}234${nb}567", Money.formatPlain(123_456_700))
        assertEquals("1${nb}234,56", Money.formatPlain(123_456))
        assertEquals("0,05", Money.formatPlain(5))
        assertEquals("0", Money.formatPlain(0))
    }

    @Test
    fun `format negative uses typographic minus and currency symbol`() {
        assertEquals("−1${nb}500$nb₽", Money.format(-150_000, CurrencyCode.RUB))
        assertEquals("12$nb\$", Money.format(1_200, CurrencyCode.USD))
        assertEquals("0,99$nb€", Money.format(99, CurrencyCode.EUR))
    }

    @Test
    fun `format handles Long extremes without overflow`() {
        assertEquals(
            "−92${nb}233${nb}720${nb}368${nb}547${nb}758,08",
            Money.formatPlain(Long.MIN_VALUE),
        )
    }

    @Test
    fun `parse accepts spaces and both separators`() {
        assertEquals(123_456L, Money.parse("1 234,56"))
        assertEquals(123_456L, Money.parse("1234.56"))
        assertEquals(123_450L, Money.parse("1${nb}234,5"))
        assertEquals(30_000L, Money.parse("  300 "))
        assertEquals(0L, Money.parse("0"))
        assertEquals(5L, Money.parse("0,05"))
        assertEquals(150L, Money.parse("1,5"))
        assertEquals(100L, Money.parse("1,"))
    }

    @Test
    fun `parse rejects invalid input`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("   "))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1,2,3"))
        assertNull(Money.parse("1,234"))
        assertNull(Money.parse("1 2a"))
        assertNull(Money.parse(",5"))
        assertNull(Money.parse("1234567890123"))
    }

    @Test
    fun `parse and formatPlain round trip`() {
        listOf(0L, 1L, 99L, 100L, 123_456L, 99_999_999_999_999L).forEach {
            assertEquals(it, Money.parse(Money.formatPlain(it)))
        }
    }

    @Test
    fun `normalizeInput groups digits and limits fraction`() {
        assertEquals("", Money.normalizeInput(""))
        assertEquals("1${nb}234", Money.normalizeInput("1234"))
        assertEquals("1${nb}234,56", Money.normalizeInput("1234.567"))
        assertEquals("0,5", Money.normalizeInput(",5"))
        assertEquals("7", Money.normalizeInput("007"))
        assertEquals("0", Money.normalizeInput("0"))
        assertEquals("12,", Money.normalizeInput("1a2,"))
        assertEquals("1${nb}000", Money.normalizeInput("1 000"))
        assertEquals("1,57", Money.normalizeInput("1,5,7")) // лишняя запятая игнорируется
    }

    @Test
    fun `ratio is null for non-positive denominator`() {
        assertNull(Money.ratio(10, 0))
        assertNull(Money.ratio(10, -5))
        assertEquals(0.25, Money.ratio(25, 100)!!, 1e-9)
        assertEquals(-1.0, Money.ratio(-100, 100)!!, 1e-9)
    }

    @Test
    fun `compareToPercent is exact at the boundary`() {
        assertEquals(0, Money.compareToPercent(30, 100, 30))
        assertEquals(1, Money.compareToPercent(3001, 10_000, 30))
        assertEquals(-1, Money.compareToPercent(2999, 10_000, 30))
    }

    @Test
    fun `ceilDiv rounds up`() {
        assertEquals(4, Money.ceilDiv(10, 3))
        assertEquals(3, Money.ceilDiv(9, 3))
        assertEquals(0, Money.ceilDiv(0, 3))
    }

    @Test
    fun `formatPercent rounds half up to one decimal`() {
        assertEquals("12,3$nb%", Money.formatPercent(0.1234))
        assertEquals("12,4$nb%", Money.formatPercent(0.1235))
        assertEquals("30$nb%", Money.formatPercent(0.3))
        assertEquals("−25$nb%", Money.formatPercent(-0.25))
        assertEquals("0$nb%", Money.formatPercent(0.0))
    }
}
