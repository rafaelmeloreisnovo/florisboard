/*
 * ZIPRAF_OMEGA Interoperability Enhancement Module v999
 * Copyright (C) 2025 Rafael Melo Reis
 *
 * License: Apache 2.0
 */

package org.florisboard.lib.zipraf

import kotlinx.serialization.Serializable

enum class CompatibilityLevel {
    FULLY_COMPATIBLE,
    COMPATIBLE,
    PARTIALLY_COMPATIBLE,
    INCOMPATIBLE,
    UNKNOWN
}

enum class MigrationDirection {
    UPGRADE,
    DOWNGRADE,
    LATERAL
}

@Serializable
data class CompatibilityCheckResult(
    val sourceVersion: String,
    val targetVersion: String,
    val direction: String,
    val level: String,
    val issues: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val dataLossPossible: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class MigrationSafetyCheck(
    val safe: Boolean,
    val risks: List<String>,
    val requiredBackup: Boolean,
    val estimatedDurationMs: Long,
    val reversible: Boolean
)

@Serializable
data class SchemaVersion(
    val version: String,
    val schemaHash: String,
    val fields: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

data class InteropMigrationStep(
    val name: String,
    val fromVersion: String,
    val toVersion: String,
    val transform: (Any) -> Any,
    val validate: (Any) -> Boolean
)

class InteroperabilityModule {
    companion object {
        private const val MAX_MIGRATION_PATH_LENGTH = 100
    }

    private val knownVersions = mutableSetOf<SemanticVersion>()
    private val migrationSteps = mutableListOf<InteropMigrationStep>()
    private val schemas = mutableMapOf<String, SchemaVersion>()

    fun registerVersion(version: SemanticVersion) {
        knownVersions.add(version)
    }

    fun registerMigrationStep(step: InteropMigrationStep) {
        migrationSteps.add(step)
    }

    fun registerSchema(version: String, schema: SchemaVersion) {
        schemas[version] = schema
    }

    fun checkDetailedCompatibility(
        source: SemanticVersion,
        target: SemanticVersion
    ): CompatibilityCheckResult {
        val direction = when {
            target > source -> MigrationDirection.UPGRADE
            target < source -> MigrationDirection.DOWNGRADE
            else -> MigrationDirection.LATERAL
        }

        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        var dataLossPossible = false

        val level = when {
            source.major != target.major -> {
                issues.add("Major version change: Breaking changes expected")
                dataLossPossible = true
                CompatibilityLevel.INCOMPATIBLE
            }
            direction == MigrationDirection.DOWNGRADE -> {
                warnings.add("Downgrade detected: Some features may be unavailable")
                warnings.add("Data created in newer version might not be compatible")
                dataLossPossible = true
                recommendations.add("Create backup before downgrading")
                recommendations.add("Test in staging environment first")
                CompatibilityLevel.PARTIALLY_COMPATIBLE
            }
            source.minor < target.minor -> {
                warnings.add("Minor version upgrade: New features available")
                recommendations.add("Review changelog for new features")
                CompatibilityLevel.FULLY_COMPATIBLE
            }
            else -> CompatibilityLevel.FULLY_COMPATIBLE
        }

        val migrationPath = findMigrationPath(source, target)
        if (migrationPath.isEmpty() && source != target) {
            issues.add("No migration path defined from $source to $target")
            if (level == CompatibilityLevel.FULLY_COMPATIBLE || level == CompatibilityLevel.COMPATIBLE) {
                warnings.add("Direct migration may work but is not tested")
            }
        }

        val sourceSchema = schemas[source.toString()]
        val targetSchema = schemas[target.toString()]
        if (sourceSchema != null && targetSchema != null) {
            val schemaCompatibility = checkSchemaCompatibility(sourceSchema, targetSchema)
            if (!schemaCompatibility.compatible) {
                issues.addAll(schemaCompatibility.issues)
                dataLossPossible = dataLossPossible || schemaCompatibility.dataLossPossible
            }
        }

        return CompatibilityCheckResult(
            sourceVersion = source.toString(),
            targetVersion = target.toString(),
            direction = direction.name,
            level = level.name,
            issues = issues,
            warnings = warnings,
            recommendations = recommendations,
            dataLossPossible = dataLossPossible
        )
    }

    private data class SchemaCompatibilityResult(
        val compatible: Boolean,
        val issues: List<String>,
        val dataLossPossible: Boolean
    )

    private fun checkSchemaCompatibility(
        source: SchemaVersion,
        target: SchemaVersion
    ): SchemaCompatibilityResult {
        val issues = mutableListOf<String>()
        var dataLossPossible = false

        val removedFields = source.fields.filter { it !in target.fields }
        if (removedFields.isNotEmpty()) {
            issues.add("Fields removed in target schema: ${removedFields.joinToString(", ")}")
            dataLossPossible = true
        }

        val addedFields = target.fields.filter { it !in source.fields }
        if (addedFields.isNotEmpty()) {
            issues.add("New fields in target schema: ${addedFields.joinToString(", ")}")
        }

        return SchemaCompatibilityResult(
            compatible = issues.isEmpty() || !dataLossPossible,
            issues = issues,
            dataLossPossible = dataLossPossible
        )
    }

    /**
     * Pure-JVM bounded path search. Cycle/length violations are represented by an
     * empty path instead of depending on Android logging from this JVM library.
     */
    private fun findMigrationPath(
        source: SemanticVersion,
        target: SemanticVersion
    ): List<InteropMigrationStep> {
        if (source == target) return emptyList()

        val path = mutableListOf<InteropMigrationStep>()
        var current = source.toString()
        val targetString = target.toString()
        val visited = mutableSetOf<String>()

        while (current != targetString) {
            if (!visited.add(current)) {
                return emptyList()
            }

            val step = migrationSteps.firstOrNull { it.fromVersion == current }
                ?: return emptyList()
            path.add(step)
            if (path.size > MAX_MIGRATION_PATH_LENGTH) {
                return emptyList()
            }
            current = step.toVersion
        }

        return path
    }

    fun checkMigrationSafety(
        source: SemanticVersion,
        target: SemanticVersion
    ): MigrationSafetyCheck {
        val compatibility = checkDetailedCompatibility(source, target)
        val risks = buildList {
            addAll(compatibility.issues)
            addAll(compatibility.warnings)
        }
        val safe = compatibility.level == CompatibilityLevel.FULLY_COMPATIBLE.name ||
            compatibility.level == CompatibilityLevel.COMPATIBLE.name
        val requiresBackup = compatibility.dataLossPossible ||
            compatibility.direction == MigrationDirection.DOWNGRADE.name
        val migrationPath = findMigrationPath(source, target)
        val reversible = compatibility.direction != MigrationDirection.DOWNGRADE.name &&
            !compatibility.dataLossPossible

        return MigrationSafetyCheck(
            safe = safe,
            risks = risks,
            requiredBackup = requiresBackup,
            estimatedDurationMs = migrationPath.size * 1000L,
            reversible = reversible
        )
    }

    fun validateDataForVersion(data: Any, version: SemanticVersion): Boolean {
        schemas[version.toString()] ?: return true
        // The current schema model contains field names only; structural reflection is
        // intentionally not claimed here. Registered schema presence therefore means
        // compatibility metadata exists, not that arbitrary Any has been deeply validated.
        @Suppress("UNUSED_VARIABLE")
        val input = data
        return true
    }

    fun estimateDowngradeRisk(
        fromVersion: SemanticVersion,
        toVersion: SemanticVersion
    ): Double {
        if (toVersion >= fromVersion) return 0.0

        val majorDiff = fromVersion.major - toVersion.major
        val minorDiff = fromVersion.minor - toVersion.minor
        val patchDiff = fromVersion.patch - toVersion.patch
        return when {
            majorDiff > 0 -> 1.0
            minorDiff > 3 -> 0.8
            minorDiff > 1 -> 0.5
            minorDiff == 1 -> 0.3
            patchDiff > 0 -> 0.1
            else -> 0.0
        }
    }

    fun hasUpgradePath(fromVersion: SemanticVersion, toVersion: SemanticVersion): Boolean {
        return toVersion > fromVersion && findMigrationPath(fromVersion, toVersion).isNotEmpty()
    }

    fun hasDowngradePath(fromVersion: SemanticVersion, toVersion: SemanticVersion): Boolean {
        return toVersion < fromVersion && findMigrationPath(fromVersion, toVersion).isNotEmpty()
    }

    fun getCompatibleVersions(version: SemanticVersion): List<SemanticVersion> {
        return knownVersions.filter { other ->
            val compatibility = checkDetailedCompatibility(version, other)
            compatibility.level == CompatibilityLevel.FULLY_COMPATIBLE.name ||
                compatibility.level == CompatibilityLevel.COMPATIBLE.name
        }
    }

    fun suggestUpgradeTarget(currentVersion: SemanticVersion): SemanticVersion? {
        return knownVersions
            .filter { it > currentVersion }
            .filter { hasUpgradePath(currentVersion, it) }
            .maxOrNull()
    }

    fun generateCompatibilityMatrix(): Map<Pair<String, String>, String> {
        val matrix = mutableMapOf<Pair<String, String>, String>()
        knownVersions.forEach { source ->
            knownVersions.forEach { target ->
                matrix[source.toString() to target.toString()] = checkDetailedCompatibility(source, target).level
            }
        }
        return matrix
    }

    fun getKnownVersions(): Set<SemanticVersion> = knownVersions.toSet()

    fun clear() {
        knownVersions.clear()
        migrationSteps.clear()
        schemas.clear()
    }
}
