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
        QuickAction.OpenPending,
        QuickAction.EditPending(7),
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

    @Test
    fun `edit pending carries its template id and needs a valid one`() {
        val intent = QuickAction.launchIntent(context, QuickAction.EditPending(42))
        assertEquals(QuickAction.EditPending(42), QuickAction.fromIntent(intent))
        assertNull(QuickAction.fromIntent(Intent(QuickAction.ACTION_EDIT_PENDING)))
        val negative = Intent(QuickAction.ACTION_EDIT_PENDING).putExtra(QuickAction.EXTRA_TEMPLATE_ID, -3L)
        assertNull(QuickAction.fromIntent(negative))
    }

    @Test
    fun `notification id travels with the launch intent only when given`() {
        val plain = QuickAction.launchIntent(context, QuickAction.OpenPending)
        assertNull(QuickAction.notificationIdFrom(plain))
        val fromNotification = QuickAction.launchIntent(context, QuickAction.OpenPending, notificationId = 2005)
        assertEquals(2005, QuickAction.notificationIdFrom(fromNotification))
        assertEquals(QuickAction.OpenPending, QuickAction.fromIntent(fromNotification))
    }
}
