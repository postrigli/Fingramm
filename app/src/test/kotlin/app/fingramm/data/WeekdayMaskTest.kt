package app.fingramm.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekdayMaskTest {
    @Test
    fun `monday is bit 0 and sunday is bit 6`() {
        assertEquals(0b0000001, weekdaysToMask(setOf(1)))
        assertEquals(0b1000000, weekdaysToMask(setOf(7)))
        assertEquals(0b0010101, weekdaysToMask(setOf(1, 3, 5)))
        assertEquals(0b1111111, weekdaysToMask((1..7).toSet()))
        assertEquals(0, weekdaysToMask(emptySet()))
    }

    @Test
    fun `every combination of days survives a round trip`() {
        for (mask in 0 until 128) {
            assertEquals(mask, weekdaysToMask(weekdaysFromMask(mask)))
        }
    }

    @Test
    fun `days outside 1 to 7 are ignored`() {
        assertEquals(0b0000011, weekdaysToMask(setOf(0, 1, 2, 8, -3)))
    }
}
