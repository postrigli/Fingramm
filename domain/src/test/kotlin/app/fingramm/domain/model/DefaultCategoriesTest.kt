package app.fingramm.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoriesTest {
    @Test
    fun `preset keys are unique`() {
        val keys = DefaultCategories.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `new presets are added only after the version the user already has`() {
        assertEquals(DefaultCategories.all.size, DefaultCategories.addedAfter(0).size)
        assertTrue(DefaultCategories.addedAfter(DefaultCategories.CURRENT_VERSION).isEmpty())
        val added = DefaultCategories.addedAfter(1).map { it.key }
        assertTrue("tobacco" in added && "alcohol" in added && "marketplaces" in added)
        assertTrue("groceries" !in added)
    }

    @Test
    fun `every preset belongs to a known version`() {
        assertTrue(DefaultCategories.all.all { it.since in 1..DefaultCategories.CURRENT_VERSION })
    }

    @Test
    fun `transfer presets exist for both directions and are excluded from totals`() {
        val transfers = DefaultCategories.all.filter { it.isTransfer }
        assertEquals(setOf(TransactionType.EXPENSE, TransactionType.INCOME), transfers.map { it.type }.toSet())
        assertTrue(transfers.all { it.since == DefaultCategories.CURRENT_VERSION })
        // Пользователи, у которых уже стоит набор версии 2, получат только переводы.
        assertEquals(transfers.map { it.key }.toSet(), DefaultCategories.addedAfter(2).map { it.key }.toSet())
    }
}
