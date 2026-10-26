// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * Duplicate-file grouping over caller-supplied records.
 *
 * Sits next to [FileSort] (ordering) and [DirectoryQuery] (selection):
 * neither answers "which of these files are byte-identical candidates".
 * Records carrying an id, byte size, and content digest are grouped by
 * (size, digest); only groups with two or more members are returned, so
 * unique files never appear in the output. Digests are caller-computed hex
 * (any proven hash — this kernel hashes nothing and therefore cannot
 * misattribute a collision); size is part of the key so a digest-only
 * mismatch can never merge different-length files. Output order is
 * deterministic (digest, then size, then sorted ids) so repeated runs and
 * cross-device comparisons agree byte-for-byte.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object DupGroup {
    const val MAX_RECORDS = 100_000

    /** One file record: stable id, byte size, lowercase hex digest. */
    data class Rec(val id: String, val bytes: Long, val digest: String)

    /** A duplicate group: the shared key plus 2+ member ids, sorted. */
    data class Group(val bytes: Long, val digest: String, val ids: List<String>)

    private val HEX = Regex("[0-9a-f]{32,128}")

    /**
     * Groups [records] into duplicate sets. Returns only groups with 2+
     * members, in deterministic order.
     *
     * @throws IllegalArgumentException on blank ids, negative sizes,
     * malformed digests, duplicate ids, or over-cap input.
     */
    fun group(records: List<Rec>): List<Group> {
        require(records.size <= MAX_RECORDS) { "record budget exceeded" }
        val seen = HashSet<String>()
        for (r in records) {
            require(r.id.isNotBlank()) { "blank id" }
            require(r.bytes >= 0) { "negative size" }
            require(HEX.matches(r.digest)) { "bad digest" }
            require(seen.add(r.id)) { "duplicate id ${r.id}" }
        }
        return records.groupBy { it.bytes to it.digest }
            .filter { it.value.size >= 2 }
            .map { (key, rs) -> Group(key.first, key.second, rs.map { it.id }.sorted()) }
            .sortedWith(compareBy({ it.digest }, { it.bytes }))
    }
}
