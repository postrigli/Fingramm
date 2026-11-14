package app.fingramm.ui.onboarding

import app.fingramm.domain.model.RecurrenceFrequency
import app.fingramm.domain.recurring.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeRowTest {
    private fun row(
        frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
        amount: Long? = 5_000_000,
        first: Int? = 10,
        second: Int? = 25,
        weekday: Int = 5,
    ) = IncomeRow(
        frequency = frequency,
        enabled = true,
        amountMinor = amount,
        firstDay = first,
        secondDay = second,
        weekday = weekday,
    )

    @Test
    fun `monthly income is one rule on the chosen day`() {
        assertEquals(listOf(Recurrence.monthly(10)), row().recurrences())
    }

    @Test
    fun `twice a month income is two rules in order of days`() {
        val rules = row(IncomeFrequency.TWICE_MONTHLY, first = 25, second = 10).recurrences()
        assertEquals(listOf(10, 25), rules.map { it.dayOfMonth })
        assertTrue(rules.all { it.frequency == RecurrenceFrequency.MONTHLY })
    }

    @Test
    fun `weekly income is one rule on the chosen weekday`() {
        assertEquals(listOf(Recurrence.weekly(setOf(3))), row(IncomeFrequency.WEEKLY, weekday = 3).recurrences())
    }

    @Test
    fun `a disabled income is always valid`() {
        assertTrue(IncomeRow().valid)
        assertTrue(IncomeRow(enabled = false, amountMinor = null, firstDay = 99).valid)
    }

    @Test
    fun `an enabled income needs an amount and a valid schedule`() {
        assertTrue(row().valid)
        assertFalse(row(amount = null).valid)
        assertFalse(row(amount = 0).valid)
        assertFalse(row(first = 0).valid)
        assertFalse(row(first = 32).valid)
        assertFalse(row(first = null).valid)
    }

    @Test
    fun `two payments in one month need two different days`() {
        assertTrue(row(IncomeFrequency.TWICE_MONTHLY, first = 5, second = 20).valid)
        assertFalse(row(IncomeFrequency.TWICE_MONTHLY, first = 5, second = 5).valid)
        assertFalse(row(IncomeFrequency.TWICE_MONTHLY, first = 5, second = null).valid)
        // Для других частот второй день не важен.
        assertTrue(row(IncomeFrequency.MONTHLY, first = 5, second = null).valid)
        assertTrue(row(IncomeFrequency.WEEKLY, first = null, second = null).valid)
    }

    @Test
    fun `onboarding state collects only enabled incomes and validates both`() {
        val state = OnboardingUiState(primaryIncome = row(), secondIncome = row(IncomeFrequency.WEEKLY))
        assertEquals(2, state.incomes.size)
        assertTrue(state.valid)
        assertEquals(0, OnboardingUiState().incomes.size)
        assertFalse(state.copy(secondIncome = row(amount = null)).valid)
        assertTrue(state.copy(primaryIncome = IncomeRow()).incomes.size == 1)
    }
}
