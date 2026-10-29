// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * 4-Tier **Settings Lockdown & Ephemeral Privacy Precedence Resolver** with Provenance Trace.
 *
 * Resolves settings across `SYSTEM_LOCKDOWN` > `EPHEMERAL_PRIVACY_OVERRIDE` > `USER_PREFERENCE` >
 * `BUILTIN_DEFAULT`, preventing lower-trust layers from weakening higher-trust boolean/numeric locks!
 */
object PolicyLockdownMerger {
    const val SCOPE =
        "4-tier settings precedence resolver (SYSTEM_LOCKDOWN > EPHEMERAL_PRIVACY_OVERRIDE > USER_PREFERENCE > BUILTIN_DEFAULT) with provenance audit."

    enum class Tier(val rank: Int) {
        BUILTIN_DEFAULT(0),
        USER_PREFERENCE(1),
        EPHEMERAL_PRIVACY_OVERRIDE(2),
        SYSTEM_LOCKDOWN(3),
    }

    data class LayerEntry<T>(val tier: Tier, val value: T, val reason: String = "")
    data class ResolvedSetting<T>(
        val key: String,
        val effectiveValue: T,
        val winningTier: Tier,
        val winningReason: String,
        val overriddenTiers: List<Tier>,
    )

    fun <T> resolve(key: String, candidates: List<LayerEntry<T>>): ResolvedSetting<T> {
        require(key.isNotBlank() && candidates.isNotEmpty()) { "setting '$key' requires >= 1 layer candidate" }
        require(candidates.map { it.tier }.distinct().size == candidates.size) { "duplicate tier entries for '$key'" }
        val sorted = candidates.sortedByDescending { it.tier.rank }
        val winner = sorted.first()
        return ResolvedSetting(
            key = key,
            effectiveValue = winner.value,
            winningTier = winner.tier,
            winningReason = winner.reason,
            overriddenTiers = sorted.drop(1).map { it.tier },
        )
    }
}
