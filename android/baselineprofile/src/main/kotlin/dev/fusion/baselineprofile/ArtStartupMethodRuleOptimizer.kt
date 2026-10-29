package dev.fusion.baselineprofile

/**
 * Clean-room ART Baseline Profile Rule Deduplicator, Hot-Startup (`HSPL`) Promotion Optimizer,
 * and Wildcard Bloat Budget Enforcer (Apache-2.0).
 */
object ArtStartupMethodRuleOptimizer {
    const val SCOPE =
        "ART baseline-prof.txt H/S/P/L flag merger + canonical class/method sorter + wildcard bloat budgeter"

    data class OptimizedProfile(
        val canonicalRules: List<String>,
        val hotStartupMethodCount: Int,
        val rejectedBroadWildcards: List<String>,
    )

    private val RULE_RE = Regex("^([HSPL]*)L([^;]+);(?:->(.+))?$")

    fun optimizeRules(rawLines: List<String>): OptimizedProfile {
        val flagsByTarget = LinkedHashMap<String, MutableSet<Char>>()
        val rejected = ArrayList<String>()

        for (raw in rawLines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            // Reject dangerous `L**;` or `Ldev/fusion/**;` whole-tree wildcards that bloat dexlayout!
            if (line.contains("L**;") || line.endsWith("/**;")) {
                rejected.add(line)
                continue
            }
            val m = RULE_RE.matchEntire(line) ?: continue
            val flags = m.groupValues[1]
            val cls = m.groupValues[2]
            val methodPart = m.groupValues[3]
            val key = if (methodPart.isEmpty()) "L$cls;" else "L$cls;->$methodPart"
            val set = flagsByTarget.getOrPut(key) { LinkedHashSet() }
            for (f in flags) set.add(f)
        }

        val canonical = flagsByTarget.entries
            .sortedBy { it.key }
            .map { (target, fset) ->
                val orderedFlags = buildString {
                    for (ch in charArrayOf('H', 'S', 'P', 'L')) if (ch in fset) append(ch)
                }
                orderedFlags + target
            }
        val hsCount = canonical.count { it.startsWith("HS") || (it.startsWith("H") && "->" in it) }
        return OptimizedProfile(canonical, hsCount, rejected)
    }
}
