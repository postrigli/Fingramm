package app.fingramm.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.fingramm.R
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** Виджет 4×2: показатели месяца и две кнопки. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthWidgetRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val october = YearMonth.of(2026, 10)

    private fun state(
        income: Long = 12_345_699,
        expenses: Long = 8_000_000,
        configured: Boolean = true,
        hidden: Boolean = false,
        startDay: Int = 1,
    ) = MonthWidgetState(october, startDay, income, expenses, CurrencyCode.RUB, configured, hidden)

    private fun inflate(state: MonthWidgetState): View =
        MonthWidgetRenderer.render(context, state).apply(context, FrameLayout(context))

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun rub(major: Long) = Money.format(major * Money.MINOR_PER_MAJOR, CurrencyCode.RUB)

    @Test
    fun `uses the month layout and has both buttons`() {
        assertEquals(R.layout.widget_month, MonthWidgetRenderer.render(context, state()).layoutId)
        val root = inflate(state())
        assertNotNull(root.findViewById<View>(R.id.widget_btn_expense))
        assertNotNull(root.findViewById<View>(R.id.widget_btn_income))
        assertNotNull(root.findViewById<View>(R.id.widget_stats))
    }

    @Test
    fun `shows the month in words with a capital letter`() {
        assertEquals("Октябрь 2026", inflate(state()).text(R.id.widget_month))
    }

    @Test
    fun `with a later start day the title shows the period dates`() {
        val title = inflate(state(startDay = 25)).text(R.id.widget_month)
        assertTrue(title, title.startsWith("25 "))
        assertTrue(title, title.contains("–") && title.contains("24 ") && title.endsWith("2026"))
    }

    @Test
    fun `shows income, expenses and balance in whole units`() {
        val root = inflate(state())
        // 123 456,99 → 123 456: копейки на виджете не показываются (отбрасываются, а не округляются).
        assertEquals(rub(123_456), root.text(R.id.widget_income_value))
        assertEquals(rub(80_000), root.text(R.id.widget_expense_value))
        assertEquals(rub(43_456), root.text(R.id.widget_balance_value))
    }

    @Test
    fun `a negative balance keeps its sign`() {
        val root = inflate(state(income = 10_000, expenses = 25_000))
        assertEquals("−" + rub(150), root.text(R.id.widget_balance_value))
    }

    @Test
    fun `hides every amount while the app lock is on`() {
        val root = inflate(state(hidden = true))
        listOf(R.id.widget_income_value, R.id.widget_expense_value, R.id.widget_balance_value)
            .forEach { assertEquals(MonthWidgetRenderer.HIDDEN, root.text(it)) }
        val description = root.findViewById<View>(R.id.widget_stats).contentDescription.toString()
        assertTrue(description.contains("скрыты"))
        assertTrue(!description.contains(rub(123_456)))
    }

    @Test
    fun `before the survey there is nothing to show`() {
        val root = inflate(state(configured = false))
        listOf(R.id.widget_income_value, R.id.widget_expense_value, R.id.widget_balance_value)
            .forEach { assertEquals("—", root.text(it)) }
    }

    @Test
    fun `the spoken description lists the three figures`() {
        val description = inflate(state()).findViewById<View>(R.id.widget_stats).contentDescription.toString()
        assertTrue(description.startsWith("Октябрь 2026"))
        listOf(rub(123_456), rub(80_000), rub(43_456)).forEach { assertTrue(description.contains(it)) }
    }
}
