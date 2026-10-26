package dev.fusion.beam

/**
 * BEP 12 announce-list semantics: a list of TIERS, each tier a list
 * of tracker URLs; clients exhaust tier 1 before tier 2; duplicates
 * within a tier are meaningless and removed (first occurrence kept).
 *
 * Declared narrowings: BEP 12 says clients SHUFFLE within a tier at
 * load — shuffling needs RNG and is runtime policy, so this kernel is
 * ORDER-PRESERVING and says so; URL syntax validation is shallow
 * (scheme http/https/udp + non-empty host), full URL parsing being
 * another kernel's job.
 */
object TrackerTier {
    const val SCOPE = "Tier structure + dedupe + priority flattening; in-tier shuffle is runtime policy, excluded."

    fun validate(tiers: List<List<String>>): List<List<String>> {
        require(tiers.isNotEmpty()) { "empty announce-list" }
        return tiers.mapIndexed { i, tier ->
            require(tier.isNotEmpty()) { "tier $i is empty" }
            val seen = LinkedHashSet<String>()
            for (url in tier) {
                require(Regex("(https?|udp)://[^/\\s]+\\S*").matches(url)) { "tracker url '$url' malformed" }
                seen.add(url)
            }
            seen.toList()
        }
    }

    /** Priority order across tiers (tier 1 fully before tier 2...). */
    fun flatten(tiers: List<List<String>>): List<String> = validate(tiers).flatten()

    fun tierOf(tiers: List<List<String>>, url: String): Int {
        validate(tiers).forEachIndexed { i, tier -> if (url in tier) return i }
        throw IllegalArgumentException("'$url' not in any tier")
    }
}
