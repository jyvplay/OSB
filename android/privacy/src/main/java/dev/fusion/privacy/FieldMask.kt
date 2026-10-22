// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Data-minimization field projection over flat string records.
 *
 * Sits next to [LogScrubber] (redacts values) and [Pseudonym] (replaces
 * identifiers): neither drops whole FIELDS a pipeline must never see.
 * [keep] returns only the named keys; [drop] returns everything except
 * them — the two directions let callers express both allowlists ("a crash
 * report may carry exactly these") and denylists ("strip PII before
 * export"). Records are flat string maps in row order; nesting is
 * explicitly outside (flatten first, or the mask cannot see inside).
 * Unknown keep-keys are NOT an error — allowlists routinely name fields
 * a sparse row lacks — but blank keys and over-cap rows are refused.
 * Masking is structural, not semantic: a kept free-text field can still
 * leak, which is [LogScrubber]'s job, not this one's.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object FieldMask {
    const val MAX_ENTRIES = 1024
    const val MAX_KEYS = 256

    /**
     * Returns the entries of [row] whose keys are in [keys], in row order.
     * Missing keys are skipped, never an error.
     */
    fun keep(row: Map<String, String>, keys: Set<String>): Map<String, String> {
        require(row.size <= MAX_ENTRIES) { "row budget exceeded" }
        require(keys.size <= MAX_KEYS) { "key budget exceeded" }
        require(keys.all { it.isNotEmpty() }) { "blank key" }
        return row.filterKeys { it in keys }
    }

    /**
     * Returns the entries of [row] whose keys are NOT in [keys], in row
     * order.
     */
    fun drop(row: Map<String, String>, keys: Set<String>): Map<String, String> {
        require(row.size <= MAX_ENTRIES) { "row budget exceeded" }
        require(keys.size <= MAX_KEYS) { "key budget exceeded" }
        require(keys.all { it.isNotEmpty() }) { "blank key" }
        return row.filterKeys { it !in keys }
    }
}
