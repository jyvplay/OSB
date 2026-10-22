// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Single-VTODO parser (RFC 5545 task subset).
 *
 * Sits next to [VEventParse] (calendar events): nothing parses the to-do half
 * of iCalendar that handoff syncs alongside events. One `BEGIN:VTODO` …
 * `END:VTODO` block yields UID (required), SUMMARY (required), STATUS
 * (NEEDS-ACTION, IN-PROGRESS, COMPLETED, CANCELLED), DUE (UTC date or
 * date-time), PRIORITY (0–9), and PERCENT-COMPLETE (0–100). Line folding
 * (CRLF followed by a space) is unfolded before parsing, and `\,`/`\;`/`\\`/`\n`
 * escapes in text values are decoded. Like [VEventParse], unknown properties
 * are refused rather than skipped so a "parsed" task never silently drops
 * scheduling data.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object VTodoParse {

    /** Task statuses from RFC 5545 §3.3.11. */
    enum class Status { NEEDS_ACTION, IN_PROGRESS, COMPLETED, CANCELLED }

    /** A parsed to-do. */
    data class Todo(
        val uid: String,
        val summary: String,
        val status: Status?,
        val due: String?,
        val priority: Int?,
        val percent: Int?,
    )

    /**
     * Parses one VTODO block.
     *
     * @throws IllegalArgumentException on malformed structure, missing UID or
     * SUMMARY, unknown properties, or out-of-range values.
     */
    fun parse(text: String): Todo {
        val lines = unfold(text)
        require(lines.size >= 2) { "short block" }
        require(lines.first() == "BEGIN:VTODO") { "no BEGIN" }
        require(lines.last() == "END:VTODO") { "no END" }
        var uid: String? = null
        var summary: String? = null
        var status: Status? = null
        var due: String? = null
        var priority: Int? = null
        var percent: Int? = null
        for (line in lines.subList(1, lines.size - 1)) {
            val name = line.substringBefore(':').substringBefore(';').uppercase()
            val value = line.substringAfter(':', "")
            require(':' in line) { "no colon" }
            when (name) {
                "UID" -> { require(value.isNotEmpty()) { "empty UID" }; uid = value }
                "SUMMARY" -> { require(value.isNotEmpty()) { "empty SUMMARY" }; summary = unescape(value) }
                "STATUS" -> status = when (value) {
                    "NEEDS-ACTION" -> Status.NEEDS_ACTION
                    "IN-PROGRESS" -> Status.IN_PROGRESS
                    "COMPLETED" -> Status.COMPLETED
                    "CANCELLED" -> Status.CANCELLED
                    else -> throw IllegalArgumentException("bad STATUS")
                }
                "DUE" -> {
                    require(value.matches(Regex("\\d{8}(T\\d{6}Z?)?"))) { "bad DUE" }
                    due = value
                }
                "PRIORITY" -> {
                    val p = value.toIntOrNull() ?: throw IllegalArgumentException("bad PRIORITY")
                    require(p in 0..9) { "PRIORITY range" }
                    priority = p
                }
                "PERCENT-COMPLETE" -> {
                    val p = value.toIntOrNull() ?: throw IllegalArgumentException("bad PERCENT")
                    require(p in 0..100) { "PERCENT range" }
                    percent = p
                }
                else -> throw IllegalArgumentException("unknown property $name")
            }
        }
        return Todo(
            uid ?: throw IllegalArgumentException("missing UID"),
            summary ?: throw IllegalArgumentException("missing SUMMARY"),
            status, due, priority, percent,
        )
    }

    private fun unfold(text: String): List<String> {
        val raw = text.split("\r\n", "\n")
        val out = mutableListOf<String>()
        for (line in raw) {
            if (line.startsWith(' ') && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + line.drop(1)
            } else {
                out += line.trimEnd('\r')
            }
        }
        return out.filter { it.isNotEmpty() }
    }

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    ',', ';', '\\' -> out.append(s[i + 1])
                    else -> throw IllegalArgumentException("bad escape")
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
