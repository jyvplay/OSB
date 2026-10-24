package dev.fusion.notes

/**
 * GitHub-Flavored-Markdown task-list items for the notes surface (GFM
 * spec §5.3 "Task list items (extension)").
 *
 * A task item is a list item whose first content is `[ ]`, `[x]` or
 * `[X]` followed by at least one space/tab or end-of-line. List markers
 * recognized: `-`, `*`, `+`, or `N.`/`N)` with N up to 9 digits, each
 * followed by at least one space. Leading indentation (0..n spaces) is
 * preserved verbatim through toggles.
 *
 * Distinct from [dev.fusion.continuity.TaskTally], which counts RFC 5545
 * VTODO STATUS values — this kernel never sees iCalendar.
 *
 * Declared narrowings: single physical lines only (lazy continuation and
 * nested-block interactions are the full parser's job); `-[ ]` without
 * the marker space is NOT a task item, per spec.
 */
object TaskCheckbox {
    const val SCOPE = "GFM task-item lines only; no lazy continuations; marker space required per spec."
    private val ITEM = Regex("^(\\s*)([-*+]|\\d{1,9}[.)])([ \\t]+)\\[( |x|X)\\](?:([ \\t]+)(.*))?$")

    data class Task(val indent: String, val marker: String, val done: Boolean, val text: String)

    fun parse(line: String): Task? {
        val m = ITEM.matchEntire(line) ?: return null
        return Task(
            indent = m.groupValues[1],
            marker = m.groupValues[2],
            done = m.groupValues[4] != " ",
            text = m.groupValues[6]
        )
    }

    fun isTask(line: String): Boolean = parse(line) != null

    fun toggle(line: String): String {
        val t = parse(line) ?: throw IllegalArgumentException("not a GFM task item: '${line.take(40)}'")
        val box = if (t.done) "[ ]" else "[x]"
        val tail = if (t.text.isEmpty() && !line.trimEnd().endsWith("]")) " " else if (t.text.isEmpty()) "" else " " + t.text
        return "${t.indent}${t.marker} $box$tail"
    }

    data class Stats(val open: Int, val done: Int) { val total: Int get() = open + done }

    fun stats(lines: List<String>): Stats {
        var open = 0
        var done = 0
        for (l in lines) parse(l)?.let { if (it.done) done++ else open++ }
        return Stats(open, done)
    }
}
