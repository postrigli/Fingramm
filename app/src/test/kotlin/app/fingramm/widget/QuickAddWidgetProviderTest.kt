package app.fingramm.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.fingramm.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAddWidgetProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun options(heightDp: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
    }

    private fun inflate(heightDp: Int): View {
        val views = QuickAddWidgetProvider.buildViews(context, options(heightDp))
        return views.apply(context, FrameLayout(context))
    }

    @Test
    fun `the default 4x2 size gets the compact layout and the 5x3 size gets the large one`() {
        assertEquals(
            R.layout.widget_quick_add,
            QuickAddWidgetProvider.buildViews(context, options(110)).layoutId,
        )
        assertEquals(
            R.layout.widget_quick_add_large,
            QuickAddWidgetProvider.buildViews(context, options(180)).layoutId,
        )
    }

    @Test
    fun `the threshold separates two and three cells`() {
        assertFalse(QuickAddWidgetProvider.isLarge(QuickAddWidgetProvider.LARGE_MIN_HEIGHT_DP - 1))
        assertTrue(QuickAddWidgetProvider.isLarge(QuickAddWidgetProvider.LARGE_MIN_HEIGHT_DP))
    }

    @Test
    fun `unknown size falls back to the compact layout`() {
        assertEquals(R.layout.widget_quick_add, QuickAddWidgetProvider.buildViews(context, null).layoutId)
        assertEquals(R.layout.widget_quick_add, QuickAddWidgetProvider.buildViews(context, Bundle()).layoutId)
    }

    @Test
    fun `compact layout has the two buttons and no shortcuts`() {
        val root = inflate(110)
        assertNotNull(root.findViewById<View>(R.id.widget_btn_expense))
        assertNotNull(root.findViewById<View>(R.id.widget_btn_income))
        assertNull(root.findViewById<View>(R.id.widget_btn_transactions))
        assertNull(root.findViewById<View>(R.id.widget_btn_dashboard))
    }

    @Test
    fun `large layout adds the shortcuts row`() {
        val root = inflate(180)
        listOf(
            R.id.widget_btn_expense,
            R.id.widget_btn_income,
            R.id.widget_btn_transactions,
            R.id.widget_btn_dashboard,
        ).forEach { assertNotNull(root.findViewById<View>(it)) }
    }

    @Test
    fun `buttons carry labels and spoken descriptions`() {
        val root = inflate(180)
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
        assertTrue(texts.containsAll(listOf("Расход", "Доход", "Операции", "Сводка")))
    }

    private fun collectTexts(view: View, out: MutableList<String>) {
        if (view is TextView) out += view.text.toString()
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectTexts(view.getChildAt(i), out)
        }
    }
}
