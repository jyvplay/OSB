// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * HTTP Range header parser pinned to RFC 9110 section 14 (June 2022):
 *
 * - ranges-specifier = range-unit "=" range-set; only the "bytes" unit is
 *   supported here (case-sensitive per the registry entry).
 * - int-range = first-pos "-" [ last-pos ]: "An int-range is invalid if
 *   the last-pos value is present and less than the first-pos" (14.1.1).
 * - suffix-range = "-" suffix-length: the final N bytes. A suffix length
 *   of 0 selects no bytes and is unsatisfiable.
 * - Satisfiability vs a known complete length L (14.1.2): a first-pos
 *   >= L is unsatisfiable; a last-pos >= L is clamped to L-1; a suffix
 *   longer than L selects the whole representation. "A valid bytes
 *   range-set that contains at least one satisfiable range is
 *   satisfiable"; when no range is satisfiable the request fails (416
 *   semantics -> refusal here).
 * - The list production (#rule) allows optional whitespace around commas;
 *   empty list elements are ignored per RFC 9110 section 5.6.1.
 *
 * Pure JVM logic; no sockets, no HTTP, no I/O.
 */
object HttpRange {
    data class Resolved(val first: Long, val last: Long) {
        val count: Long get() = last - first + 1
    }

    /** Parse and resolve against a known complete length; refusal = 416. */
    fun resolve(header: String, completeLength: Long): List<Resolved> {
        require(completeLength >= 0) { "negative complete length" }
        val eq = header.indexOf('=')
        require(eq > 0) { "missing '=' in ranges-specifier" }
        require(header.substring(0, eq) == "bytes") { "unsupported range unit" }
        val set = header.substring(eq + 1)
        val out = ArrayList<Resolved>()
        var sawValid = false
        for (rawSpec in set.split(',')) {
            val spec = rawSpec.trim(' ', '\t')
            if (spec.isEmpty()) continue // empty list elements are ignored
            sawValid = true
            val dash = spec.indexOf('-')
            require(dash >= 0) { "range-spec without '-': $spec" }
            if (dash == 0) {
                val n = digits(spec.substring(1))
                if (n == 0L || completeLength == 0L) continue // selects no bytes: unsatisfiable
                val first = if (n >= completeLength) 0L else completeLength - n
                out.add(Resolved(first, completeLength - 1))
            } else {
                val first = digits(spec.substring(0, dash))
                val lastText = spec.substring(dash + 1)
                val last = if (lastText.isEmpty()) null else digits(lastText)
                if (last != null) require(last >= first) { "last-pos < first-pos makes the int-range invalid" }
                if (first >= completeLength) continue // unsatisfiable
                val end = if (last == null || last >= completeLength) completeLength - 1 else last
                out.add(Resolved(first, end))
            }
        }
        require(sawValid) { "empty range-set" }
        require(out.isNotEmpty()) { "no satisfiable range (416)" }
        return out
    }

    private fun digits(s: String): Long {
        require(s.isNotEmpty() && s.all { it in '0'..'9' }) { "non-digit in range position: '$s'" }
        return s.toLongOrNull() ?: throw IllegalArgumentException("range position overflow")
    }
}
