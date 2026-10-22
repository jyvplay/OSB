// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/** One timestamped record awaiting a keep/purge verdict. */
data class AgedRecord(val id: String, val kind: String, val atMillis: Long)

/** Sweep outcome: id lists in input order. */
data class Sweep(val keep: List<String>, val purge: List<String>)

/**
 * Amnesia plumbing: partitions timestamped records into keep/purge by
 * per-kind max ages against a caller-supplied now (no clock reads here).
 * A record purges when strictly older than its kind's max age. Unknown
 * kinds and future timestamps are refused — unclassified data must be
 * classified, never silently kept or purged. Caps: 100k records.
 */
object RetentionSweep {
    const val MAX_RECORDS = 100_000
    private val ID = Regex("[A-Za-z0-9._\\-]{1,128}")

    fun sweep(records: List<AgedRecord>, maxAgeMs: Map<String, Long>, nowMillis: Long): Sweep {
        require(nowMillis >= 0) { "negative now" }
        require(records.size <= MAX_RECORDS) { "over $MAX_RECORDS records" }
        require(maxAgeMs.isNotEmpty() && maxAgeMs.values.all { it > 0 }) { "bad max-age table" }
        val keep = mutableListOf<String>()
        val purge = mutableListOf<String>()
        for (r in records) {
            require(ID.matches(r.id)) { "bad id ${r.id}" }
            val maxAge = maxAgeMs[r.kind] ?: throw IllegalArgumentException("unknown kind ${r.kind}")
            require(r.atMillis in 0..nowMillis) { "timestamp ${r.atMillis} outside 0..$nowMillis" }
            if (nowMillis - r.atMillis > maxAge) purge += r.id else keep += r.id
        }
        return Sweep(keep, purge)
    }
}
