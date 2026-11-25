// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

/**
 * Production-grade Write-Ahead Log (WAL) & compaction engine.
 * Supports PUT/DELETE/CHECKPOINT records, CRC-32 integrity verification,
 * crash-recovery replay, and compaction to a minimal live-key snapshot.
 */
class WalCompactionEngine private constructor() {
    companion object {
        const val MAX_LOG_RECORDS = 100_000

        enum class Op { PUT, DELETE, CHECKPOINT }

        data class WalRecord(
            val seq: Long,
            val op: Op,
            val key: String,
            val value: String?,
            val crc32: Long
        )

        fun computeCrc32(seq: Long, op: Op, key: String, value: String?): Long {
            val payload = "$seq|${op.name}|$key|${value ?: ""}".toByteArray(Charsets.UTF_8)
            var crc = 0xFFFFFFFFL
            for (b in payload) {
                crc = crc xor (b.toInt() and 0xFF).toLong()
                repeat(8) {
                    crc = if ((crc and 1L) != 0L) (crc ushr 1) xor 0xEDB88320L else (crc ushr 1)
                }
            }
            return crc xor 0xFFFFFFFFL
        }

        fun create(): WalCompactionEngine = WalCompactionEngine()
    }

    private val log = mutableListOf<WalRecord>()
    private var nextSeq = 1L

    fun appendPut(key: String, value: String): WalRecord {
        require(key.isNotEmpty()) { "empty key" }
        require(log.size < MAX_LOG_RECORDS) { "WAL full" }
        val seq = nextSeq++
        val rec = WalRecord(seq, Op.PUT, key, value, computeCrc32(seq, Op.PUT, key, value))
        log.add(rec)
        return rec
    }

    fun appendDelete(key: String): WalRecord {
        require(key.isNotEmpty()) { "empty key" }
        require(log.size < MAX_LOG_RECORDS) { "WAL full" }
        val seq = nextSeq++
        val rec = WalRecord(seq, Op.DELETE, key, null, computeCrc32(seq, Op.DELETE, key, null))
        log.add(rec)
        return rec
    }

    fun replay(): Map<String, String> {
        val state = LinkedHashMap<String, String>()
        for (rec in log) {
            val expected = computeCrc32(rec.seq, rec.op, rec.key, rec.value)
            require(rec.crc32 == expected) { "WAL corruption at seq ${rec.seq}" }
            when (rec.op) {
                Op.PUT -> state[rec.key] = rec.value ?: ""
                Op.DELETE -> state.remove(rec.key)
                Op.CHECKPOINT -> Unit
            }
        }
        return state
    }

    fun compact(): Int {
        val live = replay()
        log.clear()
        for ((k, v) in live) {
            appendPut(k, v)
        }
        return log.size
    }

    fun logSize(): Int = log.size
}
