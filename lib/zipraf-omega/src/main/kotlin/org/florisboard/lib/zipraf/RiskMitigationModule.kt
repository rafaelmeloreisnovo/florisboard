/*
 * ZIPRAF_OMEGA Risk Mitigation Module v999
 * Copyright (C) 2025 Rafael Melo Reis
 *
 * License: Apache 2.0
 */

package org.florisboard.lib.zipraf

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

enum class RiskType {
    BUG,
    LATENCY,
    FRAGMENTATION,
    REDUNDANCY,
    ZOMBIE_PROCESS,
    MEMORY_LEAK,
    DEADLOCK,
    RACE_CONDITION,
    RESOURCE_LIMIT
}

enum class RiskSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

@Serializable
data class RiskDetectionResult(
    val riskType: String,
    val severity: String,
    val detected: Boolean,
    val description: String,
    val mitigation: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metrics: Map<String, Double> = emptyMap()
)

data class LatencyMeasurement(
    val operationName: String,
    val durationMs: Long,
    val thresholdMs: Long,
    val exceedsThreshold: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class FragmentationInfo(
    val totalMemoryBytes: Long,
    val freeMemoryBytes: Long,
    val fragmentationRatio: Double,
    val isFragmented: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ZombieProcess(
    val processId: String,
    val name: String,
    val createdAt: Long,
    val lastActivityAt: Long,
    val idleTimeMs: Long,
    val isZombie: Boolean
)

class RiskMitigationModule {
    private val latencyMeasurements = ConcurrentHashMap<String, MutableList<LatencyMeasurement>>()

    private val activeProcesses = ConcurrentHashMap<String, ZombieProcess>()
    private val processRegistrationLock = Any()
    private val zombieDetectionThresholdMs = 300_000L
    private val maxActiveProcesses = 32
    private val processSlots = Semaphore(maxActiveProcesses, true)
    private val taskParallelism = 24
    private val taskDispatcher = Dispatchers.Default.limitedParallelism(taskParallelism)

    private val bugsDetected = AtomicLong(0)
    private val latencyViolations = AtomicLong(0)
    private val fragmentationEvents = AtomicLong(0)
    private val redundanciesFound = AtomicLong(0)
    private val zombiesDetected = AtomicLong(0)
    private val processLimitBreaches = AtomicLong(0)

    private val _riskEvents = MutableSharedFlow<RiskDetectionResult>(replay = 10)
    val riskEvents: SharedFlow<RiskDetectionResult> = _riskEvents.asSharedFlow()

    companion object {
        @Volatile
        private var instance: RiskMitigationModule? = null

        fun getInstance(): RiskMitigationModule {
            return instance ?: synchronized(this) {
                instance ?: RiskMitigationModule().also { instance = it }
            }
        }
    }

    suspend fun <T> measureLatency(
        operationName: String,
        thresholdMs: Long,
        operation: suspend () -> T
    ): Pair<T, LatencyMeasurement> {
        require(thresholdMs > 0) { "thresholdMs must be > 0" }
        var completed = false
        var result: T? = null
        val duration = measureTimeMillis {
            result = operation()
            completed = true
        }
        check(completed)

        val measurement = LatencyMeasurement(
            operationName = operationName,
            durationMs = duration,
            thresholdMs = thresholdMs,
            exceedsThreshold = duration > thresholdMs
        )

        latencyMeasurements.compute(operationName) { _, current ->
            (current ?: mutableListOf()).also { it.add(measurement) }
        }

        if (measurement.exceedsThreshold) {
            latencyViolations.incrementAndGet()
            _riskEvents.emit(
                RiskDetectionResult(
                    riskType = RiskType.LATENCY.name,
                    severity = if (duration > thresholdMs * 2) RiskSeverity.HIGH.name else RiskSeverity.MEDIUM.name,
                    detected = true,
                    description = "Operation '$operationName' took ${duration}ms (threshold: ${thresholdMs}ms)",
                    mitigation = "Consider optimizing the operation or increasing resources",
                    metrics = mapOf(
                        "duration_ms" to duration.toDouble(),
                        "threshold_ms" to thresholdMs.toDouble(),
                        "ratio" to duration.toDouble() / thresholdMs.toDouble()
                    )
                )
            )
        }

        @Suppress("UNCHECKED_CAST")
        return (result as T) to measurement
    }

    suspend fun checkFragmentation(): FragmentationInfo {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val usedMemory = totalMemory - freeMemory
        val fragmentationRatio = if (totalMemory > 0) {
            usedMemory.toDouble() / totalMemory.toDouble()
        } else {
            0.0
        }
        val isFragmented = fragmentationRatio > 0.80 ||
            (totalMemory < maxMemory * 0.5 && freeMemory < totalMemory * 0.2)

        val info = FragmentationInfo(
            totalMemoryBytes = totalMemory,
            freeMemoryBytes = freeMemory,
            fragmentationRatio = fragmentationRatio,
            isFragmented = isFragmented
        )

        if (isFragmented) {
            fragmentationEvents.incrementAndGet()
            _riskEvents.emit(
                RiskDetectionResult(
                    riskType = RiskType.FRAGMENTATION.name,
                    severity = if (fragmentationRatio > 0.90) RiskSeverity.HIGH.name else RiskSeverity.MEDIUM.name,
                    detected = true,
                    description = "Memory fragmentation detected: ${(fragmentationRatio * 100).toInt()}% used",
                    mitigation = "Consider triggering garbage collection or reducing memory pressure",
                    metrics = mapOf(
                        "total_bytes" to totalMemory.toDouble(),
                        "free_bytes" to freeMemory.toDouble(),
                        "fragmentation_ratio" to fragmentationRatio
                    )
                )
            )
        }
        return info
    }

    fun triggerGarbageCollection(): Boolean {
        return try {
            System.gc()
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    /**
     * Registers exactly one logical process. Duplicate IDs are idempotent and do not
     * consume another semaphore permit. Capacity checking and insertion share one lock,
     * so activeProcesses and processSlots cannot diverge due to concurrent registration.
     */
    fun registerProcess(processId: String, processName: String): Boolean {
        require(processId.isNotBlank()) { "processId must not be blank" }
        require(processName.isNotBlank()) { "processName must not be blank" }

        var limitBreached = false
        var countAtLimit = 0

        synchronized(processRegistrationLock) {
            if (activeProcesses.containsKey(processId)) {
                return true
            }

            if (!processSlots.tryAcquire()) {
                limitBreached = true
                countAtLimit = activeProcesses.size
            } else {
                val now = System.currentTimeMillis()
                activeProcesses[processId] = ZombieProcess(
                    processId = processId,
                    name = processName,
                    createdAt = now,
                    lastActivityAt = now,
                    idleTimeMs = 0,
                    isZombie = false
                )
                return true
            }
        }

        if (limitBreached) {
            processLimitBreaches.incrementAndGet()
            _riskEvents.tryEmit(
                RiskDetectionResult(
                    riskType = RiskType.RESOURCE_LIMIT.name,
                    severity = RiskSeverity.HIGH.name,
                    detected = true,
                    description = "Process limit reached: $maxActiveProcesses active processes",
                    mitigation = "Reduce concurrent processes or defer task execution",
                    metrics = mapOf(
                        "max_active_processes" to maxActiveProcesses.toDouble(),
                        "active_processes" to countAtLimit.toDouble()
                    )
                )
            )
        }
        return false
    }

    fun updateProcessActivity(processId: String) {
        activeProcesses.computeIfPresent(processId) { _, process ->
            process.copy(lastActivityAt = System.currentTimeMillis())
        }
    }

    fun unregisterProcess(processId: String) {
        synchronized(processRegistrationLock) {
            if (activeProcesses.remove(processId) != null) {
                processSlots.release()
            }
        }
    }

    suspend fun detectZombieProcesses(): List<ZombieProcess> {
        val now = System.currentTimeMillis()
        val zombies = mutableListOf<ZombieProcess>()
        activeProcesses.forEach { (id, process) ->
            val idleTime = now - process.lastActivityAt
            if (idleTime > zombieDetectionThresholdMs) {
                val zombie = process.copy(idleTimeMs = idleTime, isZombie = true)
                zombies.add(zombie)
                zombiesDetected.incrementAndGet()
                _riskEvents.emit(
                    RiskDetectionResult(
                        riskType = RiskType.ZOMBIE_PROCESS.name,
                        severity = RiskSeverity.MEDIUM.name,
                        detected = true,
                        description = "Zombie process detected: ${process.name} (ID: $id, idle: ${idleTime}ms)",
                        mitigation = "Unregister the stale logical process and investigate root cause",
                        metrics = mapOf(
                            "idle_time_ms" to idleTime.toDouble(),
                            "threshold_ms" to zombieDetectionThresholdMs.toDouble()
                        )
                    )
                )
            }
        }
        return zombies
    }

    fun cleanupZombieProcesses(zombies: List<ZombieProcess>): Int {
        var cleaned = 0
        synchronized(processRegistrationLock) {
            zombies.forEach { zombie ->
                if (activeProcesses.remove(zombie.processId) != null) {
                    processSlots.release()
                    cleaned++
                }
            }
        }
        return cleaned
    }

    suspend fun <T> detectRedundancy(data: Collection<T>): List<T> {
        val seen = mutableSetOf<T>()
        val redundant = mutableListOf<T>()
        data.forEach { item ->
            if (!seen.add(item)) redundant.add(item)
        }

        if (redundant.isNotEmpty()) {
            redundanciesFound.addAndGet(redundant.size.toLong())
            _riskEvents.emit(
                RiskDetectionResult(
                    riskType = RiskType.REDUNDANCY.name,
                    severity = if (redundant.size > data.size * 0.3) RiskSeverity.MEDIUM.name else RiskSeverity.LOW.name,
                    detected = true,
                    description = "Found ${redundant.size} redundant items out of ${data.size} total",
                    mitigation = "Remove redundant items to optimize memory usage",
                    metrics = mapOf(
                        "total_items" to data.size.toDouble(),
                        "redundant_items" to redundant.size.toDouble(),
                        "redundancy_ratio" to redundant.size.toDouble() / data.size.toDouble()
                    )
                )
            )
        }
        return redundant
    }

    fun getMetrics(): Map<String, Long> {
        return mapOf(
            "bugs_detected" to bugsDetected.get(),
            "latency_violations" to latencyViolations.get(),
            "fragmentation_events" to fragmentationEvents.get(),
            "redundancies_found" to redundanciesFound.get(),
            "zombies_detected" to zombiesDetected.get(),
            "process_limit_breaches" to processLimitBreaches.get(),
            "active_processes" to activeProcesses.size.toLong(),
            "latency_measurements" to latencyMeasurements.values.sumOf { it.size }.toLong()
        )
    }

    fun getAverageLatency(operationName: String): Double? {
        val measurements = latencyMeasurements[operationName] ?: return null
        return synchronized(measurements) {
            if (measurements.isEmpty()) null else measurements.map { it.durationMs }.average()
        }
    }

    fun startContinuousMonitoring(
        scope: CoroutineScope,
        intervalMs: Long = 60_000L
    ): Job {
        require(intervalMs > 0) { "intervalMs must be > 0" }
        return scope.launch(taskDispatcher) {
            while (isActive) {
                checkFragmentation()
                val zombies = detectZombieProcesses()
                if (zombies.isNotEmpty()) cleanupZombieProcesses(zombies)
                delay(intervalMs)
            }
        }
    }

    fun resetMetrics() {
        bugsDetected.set(0)
        latencyViolations.set(0)
        fragmentationEvents.set(0)
        redundanciesFound.set(0)
        zombiesDetected.set(0)
        processLimitBreaches.set(0)
        latencyMeasurements.clear()
        synchronized(processRegistrationLock) {
            activeProcesses.clear()
            processSlots.drainPermits()
            processSlots.release(maxActiveProcesses)
        }
    }

    suspend fun <T> runTaskAsProcess(
        processId: String,
        processName: String,
        task: suspend () -> T
    ): T? {
        if (!registerProcess(processId, processName)) return null
        return try {
            withContext(taskDispatcher) { task() }
        } finally {
            unregisterProcess(processId)
        }
    }
}
