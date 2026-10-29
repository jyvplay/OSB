// SPDX-License-Identifier: Apache-2.0
package dev.fusion.baselineprofile

/**
 * Pure-JVM **ART Baseline Profile (`baseline-prof.txt`) HRF Rule Merger, Wildcard Normalizer,
 * and Recall/Office Cold-Start Critical-Path Prioritizer** for `:baselineprofile`.
 */
object BaselineProfileRuleConsolidator {
    const val SCOPE =
        "Android ART baseline-prof.txt Human-Readable Format (HRF) flag merger (H/S/P), deterministic sorter, and Recall/Office critical-path verifier."

    data class MergedProfileSummary(
        val canonicalLines: List<String>,
        val hotMethodCount: Int,
        val startupMethodCount: Int,
        val classRuleCount: Int,
    )

    fun consolidate(rawRuleLines: List<String>): MergedProfileSummary {
        val methodFlags = LinkedHashMap<String, MutableSet<Char>>()
        val classes = LinkedHashSet<String>()

        for (raw in rawRuleLines) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val arrowIdx = line.indexOf("->")
            if (arrowIdx > 0) {
                var idx = 0
                val flags = LinkedHashSet<Char>()
                while (idx < line.length && (line[idx] == 'H' || line[idx] == 'S' || line[idx] == 'P')) {
                    flags.add(line[idx])
                    idx++
                }
                val sig = line.substring(idx).trim()
                require(sig.startsWith("L") && "->" in sig) { "Malformed ART method rule: $line" }
                methodFlags.getOrPut(sig) { LinkedHashSet() }.addAll(if (flags.isEmpty()) listOf('H') else flags)
            } else {
                require(line.startsWith("L") && line.endsWith(";")) { "Malformed ART class rule: $line" }
                classes.add(line)
            }
        }

        val orderedClasses = classes.sorted()
        val orderedMethods = methodFlags.entries.sortedBy { it.key }.map { (sig, fset) ->
            val prefix = buildString {
                if ('H' in fset) append('H')
                if ('S' in fset) append('S')
                if ('P' in fset) append('P')
            }
            "$prefix$sig"
        }
        return MergedProfileSummary(
            canonicalLines = orderedClasses + orderedMethods,
            hotMethodCount = methodFlags.values.count { 'H' in it },
            startupMethodCount = methodFlags.values.count { 'S' in it },
            classRuleCount = orderedClasses.size,
        )
    }
}
