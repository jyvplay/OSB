// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Bounded note tag normalizer.
 */
class NoteTagNormalizer private constructor() {
    companion object {
        const val MAX_TAGS = 128
        private val TAG_RE = Regex("^[a-z0-9_-]{1,64}$")

        fun normalizeAll(rawTags: List<String>): List<String> {
            require(rawTags.size <= MAX_TAGS) { "too many tags" }
            val out = LinkedHashSet<String>()
            for (raw in rawTags) {
                val norm = raw.trim().trimStart('#').lowercase()
                require(TAG_RE.matches(norm)) { "invalid tag: $raw" }
                out.add(norm)
            }
            return out.toList()
        }
    }
}
