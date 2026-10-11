package app.fingramm.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fingramm.R
import app.fingramm.ui.theme.FingrammTheme
import app.fingramm.ui.transactions.PendingActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Кнопки карточки в листе ожидающих: порядок «Отменить · Изменить · Подтвердить» и одна строка на узком экране. */
@RunWith(AndroidJUnit4::class)
class PendingActionsTest {
    @get:Rule
    val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun str(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun launch(widthDp: Int, fontScale: Float, enabled: Boolean = true) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FingrammTheme(dynamicColor = false) {
                    PendingActions(
                        enabled = enabled,
                        onCancel = { calls += "cancel" },
                        onEdit = { calls += "edit" },
                        onConfirm = { calls += "confirm" },
                        modifier = Modifier.width(widthDp.dp),
                    )
                }
            }
        }
    }

    private fun assertSingleLine(label: String) {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals("«$label» должна занимать одну строку", 1, layout.lineCount)
        assertFalse("«$label» не должна обрезаться", layout.didOverflowWidth)
    }

    private fun assertAllSingleLine() {
        listOf(R.string.pending_cancel, R.string.pending_edit, R.string.pending_confirm)
            .forEach { assertSingleLine(str(it)) }
    }

    @Test
    fun buttonsAreOrderedCancelEditConfirm() {
        launch(widthDp = 320, fontScale = 1f)
        val xs = listOf(R.string.pending_cancel, R.string.pending_edit, R.string.pending_confirm).map {
            rule.onNodeWithText(str(it)).fetchSemanticsNode().boundsInRoot.left
        }
        assertEquals(xs.sorted(), xs)
    }

    @Test
    fun labelsStayOnOneLineOnANarrowScreen() {
        launch(widthDp = 280, fontScale = 1f)
        assertAllSingleLine()
    }

    @Test
    fun labelsStayOnOneLineWithLargeSystemFont() {
        launch(widthDp = 320, fontScale = 1.3f)
        assertAllSingleLine()
    }

    @Test
    fun eachButtonCallsItsOwnAction() {
        launch(widthDp = 360, fontScale = 1f)
        rule.onNodeWithText(str(R.string.pending_cancel)).performClick()
        rule.onNodeWithText(str(R.string.pending_edit)).performClick()
        rule.onNodeWithText(str(R.string.pending_confirm)).performClick()
        assertEquals(listOf("cancel", "edit", "confirm"), calls)
    }

    @Test
    fun disabledButtonsDoNothing() {
        launch(widthDp = 360, fontScale = 1f, enabled = false)
        rule.onNodeWithText(str(R.string.pending_confirm)).performClick()
        assertTrue(calls.isEmpty())
    }
}
