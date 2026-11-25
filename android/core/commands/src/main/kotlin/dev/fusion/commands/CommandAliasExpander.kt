// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Bounded command alias expander with cycle detection.
 */
class CommandAliasExpander private constructor() {
    companion object {
        const val MAX_DEPTH = 8

        fun expand(cmd: String, aliases: Map<String, String>): String {
            var cur = cmd
            val seen = HashSet<String>()
            var depth = 0
            while (aliases.containsKey(cur)) {
                require(seen.add(cur)) { "alias cycle at $cur" }
                require(depth < MAX_DEPTH) { "alias depth exceeded" }
                cur = aliases.getValue(cur)
                depth++
            }
            return cur
        }
    }
}
