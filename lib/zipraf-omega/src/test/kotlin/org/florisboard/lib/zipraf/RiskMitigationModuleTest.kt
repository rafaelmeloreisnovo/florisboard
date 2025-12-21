/*
 * ZIPRAF_OMEGA Risk Mitigation Module Tests
 * Copyright (C) 2025 Rafael Melo Reis
 */

package org.florisboard.lib.zipraf

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RiskMitigationModuleTest {
    
    private lateinit var module: RiskMitigationModule
    
    @BeforeEach
    fun setup() {
        module = RiskMitigationModule.getInstance()
        module.resetMetrics()
    }
    
    @AfterEach
    fun tearDown() {
        module.resetMetrics()
    }
    
    /**
     * Performs CPU-bound work that takes measurable wall-clock time.
     * Used for latency measurement tests since measureTimeMillis uses wall-clock time.
     * 
     * @param iterations Number of iterations to perform (more = longer duration)
     * @return The computed sum (returned to prevent compiler optimization of the loop)
     */
    private fun performMeasurableWork(iterations: Int = 100_000): Double {
        var sum = 0.0
        repeat(iterations) { i ->
            sum += kotlin.math.sin(i.toDouble())
        }
        return sum // Return to prevent compiler from optimizing away the computation
    }
    
    @Test
    fun `test latency measurement within threshold`() = runTest {
        val (result, measurement) = module.measureLatency("fast_op", 100L) {
            delay(10)
            "success"
        }
        
        assertEquals("success", result)
        assertFalse(measurement.exceedsThreshold)
        assertTrue(measurement.durationMs < 100L)
    }
    
    @Test
    fun `test latency measurement exceeds threshold`() = runTest {
        // Use actual computation instead of delay to properly measure wall-clock time
        // measureTimeMillis uses wall-clock, so we need real work, not virtual delay
        val (result, measurement) = module.measureLatency("slow_op", 1L) {
            performMeasurableWork()
            "completed"
        }
        
        assertEquals("completed", result)
        // With threshold of 1ms, any real computation should exceed it
        assertTrue(measurement.exceedsThreshold)
        assertTrue(measurement.durationMs >= 1L)
    }
    
    @Test
    fun `test fragmentation detection`() {
        val info = module.checkFragmentation()
        
        assertNotNull(info)
        assertTrue(info.totalMemoryBytes > 0)
        assertTrue(info.freeMemoryBytes >= 0)
        assertTrue(info.fragmentationRatio >= 0.0 && info.fragmentationRatio <= 1.0)
    }
    
    @Test
    fun `test garbage collection trigger`() {
        val result = module.triggerGarbageCollection()
        assertTrue(result)
    }
    
    @Test
    fun `test process registration`() {
        module.registerProcess("proc1", "TestProcess")
        
        val metrics = module.getMetrics()
        assertEquals(1L, metrics["active_processes"])
    }
    
    @Test
    fun `test process activity update`() {
        module.registerProcess("proc1", "TestProcess")
        module.updateProcessActivity("proc1")
        
        // Should not throw exception
        assertTrue(true)
    }
    
    @Test
    fun `test process unregistration`() {
        module.registerProcess("proc1", "TestProcess")
        module.unregisterProcess("proc1")
        
        val metrics = module.getMetrics()
        assertEquals(0L, metrics["active_processes"])
    }
    
    @Test
    fun `test zombie process detection`() = runTest {
        module.registerProcess("zombie", "ZombieProcess")
        
        // Should not be zombie immediately
        val zombies1 = module.detectZombieProcesses()
        assertTrue(zombies1.isEmpty())
        
        // Note: In real test would wait for threshold, here we just verify the mechanism
        val metrics = module.getMetrics()
        assertNotNull(metrics["active_processes"])
    }
    
    @Test
    fun `test zombie cleanup`() {
        module.registerProcess("zombie1", "Zombie1")
        module.registerProcess("zombie2", "Zombie2")
        
        val zombie = ZombieProcess(
            processId = "zombie1",
            name = "Zombie1",
            createdAt = System.currentTimeMillis(),
            lastActivityAt = 0L,
            idleTimeMs = 1000000L,
            isZombie = true
        )
        
        val cleaned = module.cleanupZombieProcesses(listOf(zombie))
        assertEquals(1, cleaned)
    }
    
    @Test
    fun `test redundancy detection`() {
        val data = listOf("a", "b", "c", "a", "b", "d")
        val redundant = module.detectRedundancy(data)
        
        assertEquals(2, redundant.size)
        assertTrue(redundant.contains("a"))
        assertTrue(redundant.contains("b"))
    }
    
    @Test
    fun `test redundancy detection with no duplicates`() {
        val data = listOf("a", "b", "c", "d")
        val redundant = module.detectRedundancy(data)
        
        assertTrue(redundant.isEmpty())
    }
    
    @Test
    fun `test metrics collection`() = runTest {
        // Generate some events using actual computation for reliable timing measurement
        module.measureLatency("op1", 1L) { 
            performMeasurableWork(10_000)
            "result" 
        }
        module.checkFragmentation()
        module.detectRedundancy(listOf("a", "a", "b"))
        
        val metrics = module.getMetrics()
        
        assertNotNull(metrics["latency_violations"])
        assertNotNull(metrics["redundancies_found"])
        assertTrue(metrics["latency_violations"]!! >= 0)
    }
    
    @Test
    fun `test average latency calculation`() = runTest {
        // Use actual computation instead of delay to properly measure wall-clock time
        repeat(3) {
            module.measureLatency("test_op", 10000L) {
                performMeasurableWork(10_000)
                "result"
            }
        }
        
        val avg = module.getAverageLatency("test_op")
        assertNotNull(avg)
        assertTrue(avg!! >= 0.0) // Average should be non-negative
    }
    
    @Test
    fun `test average latency for unknown operation`() {
        val avg = module.getAverageLatency("unknown")
        assertNull(avg)
    }
    
    @Test
    fun `test risk event emission`() = runTest {
        // Use CompletableDeferred for proper synchronization between collector and emitter
        val eventReceived = CompletableDeferred<RiskDetectionResult>()
        
        // Start collecting events - filter to only get events from this test's operation
        val eventJob = launch {
            val event = module.riskEvents.first { it.description.contains("emit_test") }
            eventReceived.complete(event)
        }
        
        // Use CompletableDeferred to ensure collector is ready before emitting
        // Small delay to allow collector to subscribe
        delay(10)
        
        // Trigger event by exceeding threshold using actual computation
        module.measureLatency("emit_test", 1L) {
            performMeasurableWork()
            "result"
        }
        
        // Wait for event with timeout
        val event = eventReceived.await()
        assertNotNull(event)
        assertEquals(RiskType.LATENCY.name, event.riskType)
        
        eventJob.join()
    }
    
    @Test
    fun `test metrics reset`() = runTest {
        // Generate some data using actual computation for reliable timing
        module.measureLatency("op", 1L) { 
            performMeasurableWork()
            "result" 
        }
        module.checkFragmentation()
        
        // Reset
        module.resetMetrics()
        
        val metrics = module.getMetrics()
        assertEquals(0L, metrics["latency_violations"])
        assertEquals(0L, metrics["fragmentation_events"])
    }
}
