package app.fingramm.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalFlowGuardTest {
    @Test
    fun `nothing is expected by default`() {
        assertFalse(ExternalFlowGuard().consume())
    }

    @Test
    fun `a started flow is consumed exactly once`() {
        val guard = ExternalFlowGuard()
        guard.begin()
        assertTrue(guard.consume())
        assertFalse(guard.consume())
    }

    @Test
    fun `finishing a flow clears the exemption`() {
        val guard = ExternalFlowGuard()
        guard.begin()
        guard.end()
        assertFalse(guard.consume())
    }
}
