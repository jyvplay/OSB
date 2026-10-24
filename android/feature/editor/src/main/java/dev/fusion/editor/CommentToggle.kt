package dev.fusion.editor

/**
 * Line-comment toggling for the editor (the IDE shortcut semantics:
 * if EVERY non-blank line in the selection is commented, uncomment
 * all; otherwise comment all — so the operation is an involution on
 * uniform blocks).
 *
 * Commenting inserts `prefix + " "` at the common minimum indent of
 * the non-blank lines (keeping alignment); uncommenting removes the
 * first occurrence of the prefix and one following space if present.
 * Blank lines pass through untouched in both directions.
 *
 * Declared narrowings: LINE comments only (block comments are a
 * different shape); the prefix is caller-supplied per language —
 * no syntax table lives here; single-line strings (no '\n' handling
 * beyond the provided list of lines).
 */
object CommentToggle {
    const val SCOPE = "Line-comment toggle with min-indent alignment; prefixes are caller-supplied; no block comments."

    fun isCommented(line: String, prefix: String): Boolean =
        line.isBlank() || line.trimStart().startsWith(prefix)

    fun toggle(lines: List<String>, prefix: String): List<String> {
        require(prefix.isNotBlank()) { "empty comment prefix" }
        require(lines.isNotEmpty()) { "no lines" }
        lines.forEach { require('\n' !in it && '\r' !in it) { "lines must be split before toggling" } }
        val nonBlank = lines.filter { it.isNotBlank() }
        require(nonBlank.isNotEmpty()) { "selection is entirely blank" }
        return if (nonBlank.all { it.trimStart().startsWith(prefix) }) {
            lines.map { line ->
                if (line.isBlank()) line
                else {
                    val at = line.indexOf(prefix)
                    val after = at + prefix.length
                    val skip = if (after < line.length && line[after] == ' ') after + 1 else after
                    line.substring(0, at) + line.substring(skip)
                }
            }
        } else {
            val indent = nonBlank.minOf { line -> line.takeWhile { it == ' ' }.length }
            lines.map { line ->
                if (line.isBlank()) line
                else line.substring(0, indent) + prefix + " " + line.substring(indent)
            }
        }
    }
}
