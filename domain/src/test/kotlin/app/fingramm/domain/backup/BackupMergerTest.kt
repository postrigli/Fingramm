package app.fingramm.domain.backup

import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryKind
import app.fingramm.domain.model.CategoryLimit
import app.fingramm.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergerTest {
    private val current = TestData.backup()

    @Test
    fun `merging into an empty install adopts the backup`() {
        assertEquals(current, BackupMerger.merge(BackupData(), current))
    }

    @Test
    fun `merging the same backup twice changes nothing`() {
        assertEquals(current, BackupMerger.merge(current, current))
    }

    @Test
    fun `new transactions are added with remapped categories and fresh ids`() {
        // В импортируемом файле те же категории имеют другие id и регистр названий.
        val shifted = mapOf(1L to 10L, 2L to 20L, 3L to 30L, 4L to 40L)
        val incoming = current.copy(
            categories = current.categories.map { it.copy(id = shifted.getValue(it.id), name = it.name.uppercase()) },
            transactions = listOf(
                current.transactions[1].copy(id = 77, categoryId = 20, note = "Новая покупка", recurringId = null),
                // Дубликат существующей операции (категория 2 → 20) должен быть пропущен.
                current.transactions[1].copy(id = 78, categoryId = 20, recurringId = null),
            ),
            recurringTemplates = emptyList(),
            goals = emptyList(),
            categoryLimits = emptyList(),
        )
        val merged = BackupMerger.merge(current, incoming)

        assertEquals(current.categories, merged.categories)
        assertEquals(current.transactions.size + 1, merged.transactions.size)
        val added = merged.transactions.last()
        assertEquals("Новая покупка", added.note)
        assertEquals(2, added.categoryId)
        assertEquals(4, added.id)
        assertTrue(BackupValidator.validate(merged).isEmpty())
    }

    @Test
    fun `unknown categories are added and keep their transactions valid`() {
        val taxi = Category(50, "Такси", TransactionType.EXPENSE, CategoryKind.VARIABLE, "🚕")
        val incoming = BackupData(
            categories = listOf(taxi),
            transactions = listOf(current.transactions[2].copy(id = 1, categoryId = 50, note = "Поездка")),
            categoryLimits = listOf(CategoryLimit(50, 100_000)),
        )
        val merged = BackupMerger.merge(current, incoming)
        val newCategory = merged.categories.last()
        assertEquals("Такси", newCategory.name)
        assertEquals(5, newCategory.id)
        assertEquals(newCategory.id, merged.transactions.last().categoryId)
        assertTrue(merged.categoryLimits.any { it.categoryId == newCategory.id && it.limitMinor == 100_000L })
        assertTrue(BackupValidator.validate(merged).isEmpty())
    }

    @Test
    fun `existing limit and profile are preserved`() {
        val incoming = current.copy(
            profile = current.profile!!.copy(familySize = 9),
            categoryLimits = listOf(CategoryLimit(3, 1)),
        )
        val merged = BackupMerger.merge(current, incoming)
        assertEquals(3, merged.profile!!.familySize)
        assertEquals(listOf(CategoryLimit(3, 500_000)), merged.categoryLimits)
    }

    @Test
    fun `goals and templates are deduplicated and linked transactions follow the template`() {
        val template = current.recurringTemplates.single().copy(id = 9, amountMinor = 777)
        val incoming = BackupData(
            categories = current.categories,
            recurringTemplates = listOf(template),
            transactions = listOf(current.transactions[1].copy(id = 5, note = "Автоплатёж", recurringId = 9)),
            goals = current.goals + current.goals[0].copy(id = 9, title = "Отпуск", targetMinor = 1_000),
        )
        val merged = BackupMerger.merge(current, incoming)
        assertEquals(2, merged.recurringTemplates.size)
        assertEquals(3, merged.goals.size)
        assertEquals(merged.recurringTemplates.last().id, merged.transactions.last().recurringId)
        assertTrue(BackupValidator.validate(merged).isEmpty())
    }
}
