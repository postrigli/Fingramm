package app.fingramm.domain.goals

import app.fingramm.domain.model.Goal
import app.fingramm.domain.model.GoalHorizon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalForecastTest {
    private val today = LocalDate.of(2026, 10, 10)

    private fun goal(target: Long?, saved: Long = 0, deadline: LocalDate? = null) =
        Goal(1, "Цель", target, saved, deadline, GoalHorizon.YEAR, today)

    @Test
    fun `goal without target has no forecast`() {
        val f = GoalForecasts.forecast(goal(null, 500), 1_000, today)
        assertNull(f.progress)
        assertNull(f.monthsAtCurrentFlow)
        assertNull(f.onTrack)
        assertFalse(f.reached)
    }

    @Test
    fun `progress and months round up`() {
        val f = GoalForecasts.forecast(goal(target = 100_000, saved = 25_000), monthlyFlowMinor = 10_000, today)
        assertEquals(0.25, f.progress!!, 1e-9)
        assertEquals(75_000, f.remainingMinor)
        assertEquals(8, f.monthsAtCurrentFlow) // 7,5 → 8
        assertEquals(today.plusMonths(8), f.projectedDate)
    }

    @Test
    fun `reached goal`() {
        val f = GoalForecasts.forecast(goal(target = 100, saved = 150), 10, today)
        assertTrue(f.reached)
        assertEquals(1.0, f.progress!!, 1e-9)
        assertEquals(0, f.remainingMinor)
        assertEquals(0, f.monthsAtCurrentFlow)
    }

    @Test
    fun `non-positive flow makes the goal unreachable`() {
        assertNull(GoalForecasts.forecast(goal(100_000), 0, today).monthsAtCurrentFlow)
        assertNull(GoalForecasts.forecast(goal(100_000), -500, today).projectedDate)
    }

    @Test
    fun `required per month and on-track flag`() {
        val deadline = today.plusMonths(6)
        val f = GoalForecasts.forecast(goal(target = 120_000, deadline = deadline), 25_000, today)
        val months = GoalForecasts.monthsUntil(today, deadline)
        assertEquals(6, months)
        assertEquals(20_000L, f.requiredPerMonthMinor)
        assertEquals(true, f.onTrack)

        val behind = GoalForecasts.forecast(goal(target = 120_000, deadline = deadline), 15_000, today)
        assertEquals(false, behind.onTrack)
    }

    @Test
    fun `deadline in the past is flagged`() {
        val f = GoalForecasts.forecast(goal(target = 1_000, deadline = today.minusDays(1)), 100, today)
        assertTrue(f.deadlinePassed)
        assertNull(f.requiredPerMonthMinor)
        assertNull(f.onTrack)
    }

    @Test
    fun `months until is at least one`() {
        assertEquals(1, GoalForecasts.monthsUntil(today, today.plusDays(1)))
        assertEquals(1, GoalForecasts.monthsUntil(today, today.plusDays(30)))
        assertEquals(2, GoalForecasts.monthsUntil(today, today.plusDays(31)))
    }
}
