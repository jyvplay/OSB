// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Versioned schema migrator + invariant validator for **Recall** user preferences (delineation
 * mode, enabled 6-tier recording methods, max vault quota GiB, Tails-mode ephemeral override,
 * and automatic silence track splitting).
 */
object RecallUserPreferencesSchema {
    const val SCOPE =
        "Schema v1->v3 Recall user preferences migrator enforcing Tails ephemeral vault override and 6-method tier invariants."

    const val CURRENT_SCHEMA_VERSION = 3

    data class RecallPrefs(
        val schemaVersion: Int,
        val defaultDelineation: String,
        val enabledTiers: Set<Int>,
        val maxVaultQuotaMb: Int,
        val autoSplitSilenceTracks: Boolean,
        val stripMetadataViaMat2CleanRoom: Boolean,
        val ephemeralRamOnlyInTailsMode: Boolean,
    )

    fun migrateAndValidate(rawMap: Map<String, String>, tailsAmnesicActive: Boolean): RecallPrefs {
        val delin = when (rawMap["delineation"]?.uppercase()) {
            "BY_URL_NAME", "BY_PAGE_TITLE", "BY_CUSTOM_FOLDER" -> rawMap["delineation"]!!.uppercase()
            "HOST" -> "BY_URL_NAME" // v1 migration alias
            "TITLE" -> "BY_PAGE_TITLE"
            else -> "BY_URL_NAME"
        }
        val parsedTiers = rawMap["enabledTiers"]
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 1..6 }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: setOf(1, 2, 3, 4, 5, 6)

        val quotaMb = (rawMap["maxVaultQuotaMb"]?.toIntOrNull() ?: 4096).coerceIn(128, 262_144)
        val splitTracks = rawMap["autoSplitSilenceTracks"]?.toBooleanStrictOrNull() ?: true
        val stripMeta = if (tailsAmnesicActive) true else (rawMap["stripMetadata"]?.toBooleanStrictOrNull() ?: true)
        val ephemeral = if (tailsAmnesicActive) true else (rawMap["ephemeralRamOnly"]?.toBooleanStrictOrNull() ?: false)

        return RecallPrefs(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            defaultDelineation = delin,
            enabledTiers = parsedTiers,
            maxVaultQuotaMb = if (ephemeral) quotaMb.coerceAtMost(512) else quotaMb,
            autoSplitSilenceTracks = splitTracks,
            stripMetadataViaMat2CleanRoom = stripMeta,
            ephemeralRamOnlyInTailsMode = ephemeral,
        )
    }
}
