package org.florisboard.lib.zipraf

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RiskMitigationInvariantsTest {
    private lateinit var module: RiskMitigationModule

    @BeforeEach
    fun setUp() {
        module = RiskMitigationModule.getInstance()
        module.resetMetrics()
    }

    @AfterEach
    fun tearDown() {
        module.resetMetrics()
    }

    @Test
    fun `duplicate process id is idempotent and consumes one slot`() {
        assertTrue(module.registerProcess("same", "first"))
        assertTrue(module.registerProcess("same", "second"))
        assertEquals(1L, module.getMetrics()["active_processes"])

        for (index in 1..31) {
            assertTrue(module.registerProcess("p$index", "Process$index"))
        }
        assertEquals(32L, module.getMetrics()["active_processes"])
        assertFalse(module.registerProcess("overflow", "Overflow"))
    }

    @Test
    fun `zombie cleanup returns capacity to semaphore`() {
        for (index in 1..32) {
            assertTrue(module.registerProcess("p$index", "Process$index"))
        }
        assertFalse(module.registerProcess("blocked", "Blocked"))

        val removed = module.cleanupZombieProcesses(
            listOf(
                ZombieProcess(
                    processId = "p1",
                    name = "Process1",
                    createdAt = 0L,
                    lastActivityAt = 0L,
                    idleTimeMs = Long.MAX_VALUE,
                    isZombie = true
                )
            )
        )

        assertEquals(1, removed)
        assertTrue(module.registerProcess("replacement", "Replacement"))
        assertEquals(32L, module.getMetrics()["active_processes"])
    }

    @Test
    fun `reset restores all process capacity`() {
        repeat(32) { index ->
            assertTrue(module.registerProcess("before$index", "Before$index"))
        }
        module.resetMetrics()
        repeat(32) { index ->
            assertTrue(module.registerProcess("after$index", "After$index"))
        }
        assertFalse(module.registerProcess("after-overflow", "AfterOverflow"))
    }
}
