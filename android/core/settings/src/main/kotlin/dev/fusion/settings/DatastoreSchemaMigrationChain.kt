// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Pure-JVM **Versioned Schema Migration Chain & Unknown-Field Forward-Compatibility Preserver**
 * for `:core:datastore`.
 */
object DatastoreSchemaMigrationChain {
    const val SCOPE =
        "Deterministic multi-hop DataStore schema migrator preserving unrecognized forward-compatible preference keys."

    fun interface MigrationStep {
        fun migrate(fields: Map<String, String>): Map<String, String>
    }

    class Registry(private val stepsBySourceVersion: Map<Int, MigrationStep>) {
        fun upgrade(currentVersion: Int, targetVersion: Int, rawFields: Map<String, String>): Map<String, String> {
            require(currentVersion >= 1 && targetVersion >= currentVersion) {
                "Invalid schema version range $currentVersion -> $targetVersion"
            }
            var curMap = LinkedHashMap(rawFields)
            for (v in currentVersion until targetVersion) {
                val step = stepsBySourceVersion[v]
                    ?: throw IllegalStateException("Missing DataStore migration step for v$v -> v${v + 1}")
                val migrated = step.migrate(curMap)
                val merged = LinkedHashMap(curMap)
                merged.putAll(migrated)
                merged["__schema_version"] = (v + 1).toString()
                curMap = merged
            }
            return curMap
        }
    }
}
