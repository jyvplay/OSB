package dev.fusion.editor

/**
 * Duplicate-line reporting and removal for the editor's cleanup
 * command. Exact-match semantics (byte-equal lines; trailing spaces
 * matter — normalizing first is a different, caller-chosen step and
 * silently trimming would make the report lie about the buffer).
 *
 * Declared narrowings: whole physical lines only; 1-based line
 * numbers (editor convention); removal keeps the FIRST occurrence so
 * document order is stable.
 */
object DupLines {
    const val SCOPE = "Exact duplicate lines: report groups + keep-first removal; no normalization, by contract."

    /** Groups of line numbers (1-based) sharing identical content, only where count > 1, in first-seen order. */
    fun groups(lines: List<String>): List<List<Int>> {
        val seen = LinkedHashMap<String, MutableList<Int>>()
        lines.forEachIndexed { i, l -> seen.getOrPut(l) { ArrayList() }.add(i + 1) }
        return seen.values.filter { it.size > 1 }
    }

    fun dedupeKeepFirst(lines: List<String>): List<String> {
        val seen = HashSet<String>()
        return lines.filter { seen.add(it) }
    }

    fun duplicateCount(lines: List<String>): Int = lines.size - dedupeKeepFirst(lines).size
}
