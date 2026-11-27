// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Bounded wiki-style backlink resolver. Extracts [[backlink target|display
 * text]] and [[bare target]] references from markdown-like text, resolves
 * each target to a normalized note id, and returns the set of referenced
 * note ids plus the display text for each. Targets are normalized by
 * lowercasing and replacing spaces with underscores.
 *
 * Declared narrowings: input at most [MAX_INPUT] chars; at most
 * [MAX_LINKS] links per document; each target at most [MAX_TARGET_LEN]
 * chars; display text at most [MAX_DISPLAY_LEN] chars; only ASCII letters,
 * digits, space, hyphen, underscore, and pipe are admitted in targets;
 * unmatched brackets are treated as literal text, not links.
 */
class BacklinkResolver private constructor() {
    companion object {
        const val MAX_INPUT = 1 shl 14  // 16 KiB
        const val MAX_LINKS = 512
        const val MAX_TARGET_LEN = 256
        const val MAX_DISPLAY_LEN = 512
        private val LINK = Regex("\\[\\[([^\\]|\\]]+)(\\|([^\\]]*))?\\]\\]")

        data class Link(
            val target: String,
            val display: String?,
            val noteId: String
        )

        fun empty(): BacklinkResolver = BacklinkResolver()
    }

    fun resolve(input: String): List<Link> {
        require(input.length <= MAX_INPUT) { "input exceeds $MAX_INPUT chars" }
        val links = mutableListOf<Link>()
        var lastEnd = 0
        val m = LINK.matchEntire(input)
        // Use findAll for non-overlapping matches
        var searchFrom = 0
        while (true) {
            val found = LINK.find(input, searchFrom) ?: break
            if (links.size >= MAX_LINKS) break
            val groupValues = found.groupValues
            val rawTarget = groupValues[1]
            val display = found.groups[3]?.value
            if (rawTarget.isNotEmpty() && rawTarget.length <= MAX_TARGET_LEN && validTarget(rawTarget)) {
                val noteId = normalize(rawTarget)
                val disp = if (display != null && display.length <= MAX_DISPLAY_LEN) display else null
                links.add(Link(rawTarget, disp, noteId))
            }
            searchFrom = found.range.last + 1
        }
        return links.toList()
    }

    fun noteIds(input: String): Set<String> = resolve(input).map { it.noteId }.toSet()

    private fun validTarget(s: String): Boolean {
        return s.all { c ->
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' ||
                    c == ' ' || c == '-' || c == '_' || c == '|'
        }
    }

    private fun normalize(s: String): String {
        return s.lowercase().replace(" ", "_").take(MAX_TARGET_LEN)
    }
}
