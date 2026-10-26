// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * k-anonymity audit over quasi-identifier rows.
 *
 * Sits next to [FieldMask] (which projects rows down to releasable
 * fields) and [Pseudonym] (which replaces identifiers): neither answers
 * "is every quasi-identifier combination shared by at least k rows".
 * Rows are caller-projected value lists — grouping sees only what the
 * caller chose to release, so a forgotten column cannot silently weaken
 * the audit. The report names every violating group key with its size,
 * plus the minimum group size, so remediation (suppress or generalize)
 * knows exactly where to aim. k = 1 passes vacuously and is allowed but
 * documented as meaningless; empty and ragged inputs are refused, because
 * an audit over no (or misaligned) data would certify nothing.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object KAnon {
    const val MAX_ROWS = 100_000
    const val MAX_COLS = 64

    /** One violating group: the shared key plus its row count. */
    data class Violation(val key: List<String>, val size: Int)

    /** Audit report: group count, minimum size, pass flag, violations. */
    data class Audit(val groups: Int, val minSize: Int, val ok: Boolean, val violations: List<Violation>)

    /**
     * Audits [rows] for k-anonymity at threshold [k].
     *
     * @throws IllegalArgumentException on k < 1, empty input, ragged rows,
     * or over-cap input.
     */
    fun audit(rows: List<List<String>>, k: Int): Audit {
        require(k >= 1) { "bad k" }
        require(rows.isNotEmpty()) { "no rows" }
        require(rows.size <= MAX_ROWS) { "row budget exceeded" }
        val cols = rows[0].size
        require(cols in 1..MAX_COLS) { "bad columns" }
        require(rows.all { it.size == cols }) { "ragged rows" }
        val groups = rows.groupBy { it }
        val min = groups.values.minOf { it.size }
        val bad = groups.filter { it.value.size < k }
            .map { Violation(it.key, it.value.size) }
            .sortedWith(compareBy({ it.size }, { it.key.joinToString("\u0000") }))
        return Audit(groups.size, min, bad.isEmpty(), bad)
    }
}
