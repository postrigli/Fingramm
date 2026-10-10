package app.fingramm.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.fingramm.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Виджет 2×2: только две кнопки, без данных. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAddWidgetProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun inflate(): View = QuickAddWidgetProvider.buildViews(context).apply(context, FrameLayout(context))

    @Test
    fun `uses the compact two-button layout`() {
        assertEquals(R.layout.widget_quick_add, QuickAddWidgetProvider.buildViews(context).layoutId)
    }

    @Test
    fun `has the two buttons and shows no statistics`() {
        val root = inflate()
        assertNotNull(root.findViewById<View>(R.id.widget_btn_expense))
        assertNotNull(root.findViewById<View>(R.id.widget_btn_income))
        assertNull(root.findViewById<View>(R.id.widget_stats))
        assertNull(root.findViewById<View>(R.id.widget_income_value))
    }

    @Test
    fun `buttons carry labels and spoken descriptions`() {
        val root = inflate()
        assertEquals(
            context.getString(R.string.widget_expense_cd),
            root.findViewById<View>(R.id.widget_btn_expense).contentDescription,
        )
        assertEquals(
            context.getString(R.string.widget_income_cd),
            root.findViewById<View>(R.id.widget_btn_income).contentDescription,
        )
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        assertTrue(texts.containsAll(listOf("Расход", "Доход")))
    }

    private fun collectTexts(view: View, out: MutableList<String>) {
        if (view is TextView) out += view.text.toString()
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectTexts(view.getChildAt(i), out)
        }
    }
}
