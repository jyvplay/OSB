// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Bounded shell-alias table with cycle-safe expansion. Only argv[0] is
 * resolved, repeatedly, up to MAX_DEPTH hops; a revisit (a->b->a) or depth
 * overflow refuses instead of looping. Aliases never shadow through
 * arguments, and removal is explicit — there is no implicit rehash.
 */
class AliasTable private constructor(private val map: LinkedHashMap<String, List<String>>) {
    companion object {
        const val MAX_ALIASES = 64
        const val MAX_DEPTH = 8
        const val MAX_TOKENS = 8
        const val MAX_TOKEN = 64
        private val NAME = Regex("[a-z][a-z0-9-]{0,31}")

        fun validName(s: String) = NAME.matches(s)
        fun validToken(s: String) = s.isNotEmpty() && s.length <= MAX_TOKEN && '\u0000' !in s
        fun empty(): AliasTable = AliasTable(LinkedHashMap())
    }

    fun names(): Set<String> = map.keys.toSet()
    fun get(name: String): List<String>? = map[name]?.toList()

    fun define(name: String, expansion: List<String>) {
        require(validName(name))
        require(expansion.isNotEmpty() && expansion.size <= MAX_TOKENS)
        require(expansion.all(::validToken))
        if (!map.containsKey(name)) require(map.size < MAX_ALIASES)
        map[name] = expansion.toList()
    }

    fun undefine(name: String): Boolean = map.remove(name) != null
    fun clear() = map.clear()

    fun expand(argv: List<String>): List<String> {
        require(argv.isNotEmpty() && argv.all(::validToken))
        val seen = HashSet<String>()
        var head = argv[0]; var tail = argv.drop(1); var hops = 0
        while (true) {
            val exp = map[head] ?: return listOf(head) + tail
            require(seen.add(head)) { "alias cycle at $head" }
            require(++hops <= MAX_DEPTH) { "alias depth exceeded" }
            head = exp[0]; tail = exp.drop(1) + tail
        }
    }
}
