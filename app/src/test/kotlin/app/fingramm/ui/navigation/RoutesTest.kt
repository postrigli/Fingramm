package app.fingramm.ui.navigation

import app.fingramm.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class RoutesTest {
    @Test
    fun `new transaction route without a type keeps the old shape`() {
        assertEquals("transaction/edit?id=-1", Routes.transactionEdit())
    }

    @Test
    fun `editing an existing transaction does not carry a type`() {
        assertEquals("transaction/edit?id=42", Routes.transactionEdit(42))
    }

    @Test
    fun `widget routes carry the requested type`() {
        assertEquals("transaction/edit?id=-1&type=INCOME", Routes.transactionEdit(type = TransactionType.INCOME))
        assertEquals("transaction/edit?id=-1&type=EXPENSE", Routes.transactionEdit(type = TransactionType.EXPENSE))
    }

    @Test
    fun `editing a pending occurrence carries the template id`() {
        assertEquals("transaction/edit?id=-1&template=9", Routes.transactionEdit(template = 9))
    }

    @Test
    fun `category stats and report routes carry the period`() {
        assertEquals("category-stats/12?period=2026-09", Routes.categoryStats(12, YearMonth.of(2026, 9)))
        assertEquals("report?period=2026-09", Routes.report(YearMonth.of(2026, 9)))
        assertEquals("report", Routes.report())
    }
}
