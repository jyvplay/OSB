package dev.fusion.settings

import java.security.MessageDigest

/**
 * Clean-room Schema-Versioned Transactional Settings Migration Engine with Atomic Rollback &
 * Canonical SHA-256 Digest Pinning (Apache-2.0).
 */
object TransactionalSettingsMigrationEngine {
    const val SCOPE =
        "Sequential schema-versioned settings migrator with invariant validation, atomic rollback & canonical SHA-256 digest"

    data class MigrationStep(
        val fromVersion: Int,
        val toVersion: Int,
        val transform: (Map<String, String>) -> Map<String, String>,
    ) {
        init {
            require(toVersion == fromVersion + 1) { "Migration step must advance by +1 ($fromVersion -> $toVersion)" }
        }
    }

    data class MigrationOutcome(
        val succeeded: Boolean,
        val finalVersion: Int,
        val state: Map<String, String>,
        val canonicalSha256Hex: String,
        val errorMessage: String? = null,
    )

    fun migrate(
        initialVersion: Int,
        targetVersion: Int,
        initialState: Map<String, String>,
        steps: List<MigrationStep>,
        postValidator: (Map<String, String>) -> Boolean = { true },
    ): MigrationOutcome {
        require(targetVersion >= initialVersion) { "Downgrade ($initialVersion -> $targetVersion) refused" }
        val snapshotBefore = LinkedHashMap(initialState.toSortedMap())
        val stepByFrom = steps.associateBy { it.fromVersion }
        var curVer = initialVersion
        var curMap: Map<String, String> = snapshotBefore

        try {
            while (curVer < targetVersion) {
                val step = stepByFrom[curVer] ?: error("Missing migration step from v$curVer")
                curMap = step.transform(curMap).toSortedMap()
                curVer = step.toVersion
            }
            check(postValidator(curMap)) { "Post-migration invariant check failed at v$curVer" }
            return MigrationOutcome(true, curVer, curMap, digestMap(curMap))
        } catch (t: Throwable) {
            return MigrationOutcome(false, initialVersion, snapshotBefore, digestMap(snapshotBefore), t.message)
        }
    }

    private fun digestMap(m: Map<String, String>): String {
        val canonical = m.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
