// SPDX-License-Identifier: Apache-2.0
package dev.fusion.native

import java.io.ByteArrayOutputStream

/**
 * Clean-room canonical sorted disjoint interval / RLE bitset with LEB128 binary codec.
 *
 * Completes the `RleBits` native-format primitive queued in R44-ALTERNATIVES:
 * - Stores non-negative integers as canonical sorted, non-adjacent, non-empty half-open runs
 *   `[start, start + length)` so adjacent/overlapping runs always coalesce to a unique normal form.
 * - Supports `O(log R)` membership (`contains`), exact `Long` `cardinality`, set algebra
 *   (`union`, `intersect`, `difference`), and compact unsigned-LEB128 `(gap, length - 1)` wire
 *   encoding with fail-closed run-count and materialization caps against RLE bombs.
 */
object RleBitset {
    const val SCOPE =
        "Canonical non-negative interval/RLE bitset (union, intersect, difference, O(log R) lookup, LEB128 wire codec with bomb caps); Roaring 16-bit container hybrids out of scope."

    const val MAGIC_0: Byte = 0x52 // 'R'
    const val MAGIC_1: Byte = 0x42 // 'B'
    const val VERSION: Byte = 1
    const val MAX_RUNS = 65_536
    const val MAX_MATERIALIZE = 1_048_576

    data class Run(val start: Int, val length: Int) {
        init {
            require(start >= 0) { "run start $start must be >= 0" }
            require(length > 0) { "run length $length must be > 0" }
            require(start.toLong() + length.toLong() <= Int.MAX_VALUE.toLong()) { "run end overflows Int" }
        }
        val endExclusive: Int get() = start + length
    }

    data class Bitset(val runs: List<Run>) {
        val cardinality: Long = runs.sumOf { it.length.toLong() }

        fun isEmpty(): Boolean = runs.isEmpty()

        fun contains(value: Int): Boolean {
            if (value < 0 || runs.isEmpty()) return false
            var lo = 0
            var hi = runs.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val r = runs[mid]
                when {
                    value < r.start -> hi = mid - 1
                    value >= r.endExclusive -> lo = mid + 1
                    else -> return true
                }
            }
            return false
        }

        fun toIntList(): List<Int> {
            require(cardinality <= MAX_MATERIALIZE) {
                "cardinality $cardinality exceeds materialization cap $MAX_MATERIALIZE"
            }
            val out = ArrayList<Int>(cardinality.toInt())
            for (r in runs) {
                for (v in r.start until r.endExclusive) out.add(v)
            }
            return out
        }
    }

    fun empty(): Bitset = Bitset(emptyList())

    fun ofInts(values: Iterable<Int>): Bitset {
        val sorted = values.toList().onEach { require(it >= 0) { "negative bit index $it" } }.sorted()
        if (sorted.isEmpty()) return empty()
        val runs = ArrayList<Run>()
        var runStart = sorted[0]
        var prev = sorted[0]
        for (i in 1 until sorted.size) {
            val v = sorted[i]
            if (v == prev) continue
            if (v == prev + 1) {
                prev = v
            } else {
                require(runs.size < MAX_RUNS) { "run count exceeds $MAX_RUNS" }
                runs.add(Run(runStart, prev - runStart + 1))
                runStart = v
                prev = v
            }
        }
        require(runs.size < MAX_RUNS) { "run count exceeds $MAX_RUNS" }
        runs.add(Run(runStart, prev - runStart + 1))
        return Bitset(runs)
    }

    fun ofRuns(rawRuns: List<Run>): Bitset {
        if (rawRuns.isEmpty()) return empty()
        val sorted = rawRuns.sortedWith(compareBy<Run> { it.start }.thenBy { it.endExclusive })
        val merged = ArrayList<Run>(minOf(sorted.size, MAX_RUNS))
        var curStart = sorted[0].start
        var curEnd = sorted[0].endExclusive
        for (i in 1 until sorted.size) {
            val nxt = sorted[i]
            if (nxt.start <= curEnd) {
                curEnd = maxOf(curEnd, nxt.endExclusive)
            } else {
                require(merged.size < MAX_RUNS) { "run count exceeds $MAX_RUNS" }
                merged.add(Run(curStart, curEnd - curStart))
                curStart = nxt.start
                curEnd = nxt.endExclusive
            }
        }
        require(merged.size < MAX_RUNS) { "run count exceeds $MAX_RUNS" }
        merged.add(Run(curStart, curEnd - curStart))
        return Bitset(merged)
    }

    fun union(a: Bitset, b: Bitset): Bitset = ofRuns(a.runs + b.runs)

    fun intersect(a: Bitset, b: Bitset): Bitset {
        val out = ArrayList<Run>()
        var i = 0
        var j = 0
        while (i < a.runs.size && j < b.runs.size) {
            val ra = a.runs[i]
            val rb = b.runs[j]
            val start = maxOf(ra.start, rb.start)
            val end = minOf(ra.endExclusive, rb.endExclusive)
            if (start < end) {
                require(out.size < MAX_RUNS) { "run count exceeds $MAX_RUNS" }
                out.add(Run(start, end - start))
            }
            if (ra.endExclusive < rb.endExclusive) i++ else j++
        }
        return Bitset(out)
    }

    fun difference(a: Bitset, b: Bitset): Bitset {
        if (a.isEmpty() || b.isEmpty()) return a
        val out = ArrayList<Run>()
        var j = 0
        for (ra in a.runs) {
            var cur = ra.start
            val end = ra.endExclusive
            while (j < b.runs.size && b.runs[j].endExclusive <= cur) j++
            var k = j
            while (k < b.runs.size && b.runs[k].start < end) {
                val rb = b.runs[k]
                if (rb.start > cur) {
                    require(out.size < MAX_RUNS) { "run count exceeds $MAX_RUNS" }
                    out.add(Run(cur, rb.start - cur))
                }
                cur = maxOf(cur, rb.endExclusive)
                if (cur >= end) break
                k++
            }
            if (cur < end) {
                require(out.size < MAX_RUNS) { "run count exceeds $MAX_RUNS" }
                out.add(Run(cur, end - cur))
            }
        }
        return Bitset(out)
    }

    fun encode(bitset: Bitset): ByteArray {
        require(bitset.runs.size <= MAX_RUNS) { "run count exceeds $MAX_RUNS" }
        val out = ByteArrayOutputStream(4 + bitset.runs.size * 2)
        out.write(MAGIC_0.toInt())
        out.write(MAGIC_1.toInt())
        out.write(VERSION.toInt())
        writeUleb(out, bitset.runs.size)
        var prevEnd = 0
        for ((idx, r) in bitset.runs.withIndex()) {
            val gap = if (idx == 0) r.start else {
                require(r.start > prevEnd) { "non-canonical adjacent/unsorted run at $idx" }
                r.start - prevEnd - 1
            }
            writeUleb(out, gap)
            writeUleb(out, r.length - 1)
            prevEnd = r.endExclusive
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Bitset {
        require(bytes.size >= 4) { "RleBitset payload too short (${bytes.size} bytes)" }
        require(bytes[0] == MAGIC_0 && bytes[1] == MAGIC_1) { "bad RleBitset magic" }
        require(bytes[2] == VERSION) { "unsupported RleBitset version ${bytes[2]}" }
        val cursor = intArrayOf(3)
        val count = readUleb(bytes, cursor)
        require(count in 0..MAX_RUNS) { "RleBitset run count $count exceeds $MAX_RUNS" }
        val runs = ArrayList<Run>(count)
        var prevEnd = 0L
        for (idx in 0 until count) {
            val gap = readUleb(bytes, cursor).toLong()
            val lenMinus1 = readUleb(bytes, cursor).toLong()
            val start = if (idx == 0) gap else prevEnd + 1L + gap
            val length = lenMinus1 + 1L
            require(start >= 0L && length > 0L && start + length <= Int.MAX_VALUE.toLong()) {
                "RleBitset run overflow at index $idx"
            }
            runs.add(Run(start.toInt(), length.toInt()))
            prevEnd = start + length
        }
        require(cursor[0] == bytes.size) { "trailing ${bytes.size - cursor[0]} bytes after RleBitset" }
        return Bitset(runs)
    }

    private fun writeUleb(out: ByteArrayOutputStream, value: Int) {
        require(value >= 0) { "negative ULEB128 value $value" }
        var v = value
        while (true) {
            if ((v and 0x7F.inv()) == 0) {
                out.write(v)
                return
            }
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    private fun readUleb(bytes: ByteArray, cursor: IntArray): Int {
        var result = 0
        var shift = 0
        while (shift < 35) {
            require(cursor[0] < bytes.size) { "truncated ULEB128 varint" }
            val b = bytes[cursor[0]++].toInt() and 0xFF
            if (shift == 28 && (b and 0xF0) != 0) {
                throw IllegalArgumentException("ULEB128 overflows 31-bit non-negative Int")
            }
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) {
                require(shift == 0 || (b and 0x7F) != 0) { "overlong non-canonical ULEB128 varint" }
                return result
            }
            shift += 7
        }
        throw IllegalArgumentException("ULEB128 exceeds 5 bytes")
    }
}
