// SPDX-License-Identifier: Apache-2.0
package dev.fusion.database

import java.util.TreeMap

/**
 * Production-grade sorted MemTable with tombstone deletes, range scans,
 * and byte-size flush threshold tracking.
 */
class DatabaseMemTable private constructor(private val flushThresholdBytes: Long) {
    companion object {
        fun create(flushThresholdBytes: Long = 4L * 1024L * 1024L): DatabaseMemTable {
            require(flushThresholdBytes > 0L) { "invalid flushThresholdBytes" }
            return DatabaseMemTable(flushThresholdBytes)
        }
    }

    private val map = TreeMap<String, String?>() // null = tombstone
    var approxBytes: Long = 0L
        private set

    fun put(key: String, value: String) {
        require(key.isNotEmpty()) { "empty key" }
        val old = map.put(key, value)
        if (old == null) approxBytes += key.length.toLong()
        else approxBytes -= old.length.toLong()
        approxBytes += value.length.toLong()
    }

    fun delete(key: String) {
        require(key.isNotEmpty()) { "empty key" }
        val old = map.put(key, null)
        if (old != null) approxBytes -= old.length.toLong()
        else if (key !in map) approxBytes += key.length.toLong()
    }

    fun get(key: String): String? = map[key]

    fun scanRange(startInclusive: String, endExclusive: String): List<Pair<String, String>> =
        map.subMap(startInclusive, true, endExclusive, false)
            .entries
            .mapNotNull { (k, v) -> if (v != null) k to v else null }

    fun shouldFlush(): Boolean = approxBytes >= flushThresholdBytes
}
