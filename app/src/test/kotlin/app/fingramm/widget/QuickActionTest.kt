package app.fingramm.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.fingramm.MainActivity
import app.fingramm.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickActionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val all = listOf(
        QuickAction.AddTransaction(TransactionType.EXPENSE),
        QuickAction.AddTransaction(TransactionType.INCOME),
        QuickAction.OpenDashboard,
    )

    @Test
    fun `every action survives a round trip through the launch intent`() {
        all.forEach { action ->
            assertEquals(action, QuickAction.fromIntent(QuickAction.launchIntent(context, action)))
        }
    }

    @Test
    fun `intent actions are distinct so that PendingIntents do not collide`() {
        assertEquals(all.size, all.map { it.intentAction }.toSet().size)
    }

    @Test
    fun `launch intent targets the main activity and reuses the running instance`() {
        val intent = QuickAction.launchIntent(context, QuickAction.OpenDashboard)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun `ordinary launches and foreign actions are not quick actions`() {
        assertNull(QuickAction.fromIntent(null))
        assertNull(QuickAction.fromIntent(Intent()))
        assertNull(QuickAction.fromIntent(Intent(Intent.ACTION_MAIN)))
        assertNull(QuickAction.fromIntent(Intent("app.fingramm.action.DELETE_EVERYTHING")))
    }

    @Test
    fun `extras cannot change which action is requested`() {
        val intent = Intent(QuickAction.ACTION_ADD_EXPENSE).putExtra("type", "INCOME")
        assertEquals(QuickAction.AddTransaction(TransactionType.EXPENSE), QuickAction.fromIntent(intent))
    }
}
