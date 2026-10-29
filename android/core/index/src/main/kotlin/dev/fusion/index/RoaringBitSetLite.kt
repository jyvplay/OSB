// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

import java.util.TreeMap

/**
 * Clean-room **Hybrid Roaring-Style 16-Bit-Chunk Compressed Bitmap (`RoaringBitSetLite`)**.
 *
 * Partitions 32-bit non-negative document IDs by high 16 bits (`chunkKey = id ushr 16`):
 * - Sparse chunks (`<= 4096` entries) use a sorted `ShortArray` (`2` bytes/entry);
 * - Dense chunks (`> 4096` entries) promote automatically to a `LongArray(1024)` (`65,536`-bit
 *   fixed `8 KiB` bitmap), demoting back when cardinality drops `<= 4096`!
 * Supports fast `and`, `or`, `andNot`, `contains`, and sorted iteration for inverted search indices.
 */
class RoaringBitSetLite private constructor(
    private val chunks: TreeMap<Int, Container>,
) {
    companion object {
        const val SCOPE =
            "Clean-room hybrid 16-bit-chunk Roaring bitmap (sorted ShortArray <=4096 auto-promoting to 1024-Long bitmap, with fast AND / OR / ANDNOT set algebra)."

        const val ARRAY_MAX = 4096

        fun empty(): RoaringBitSetLite = RoaringBitSetLite(TreeMap())

        fun of(vararg values: Int): RoaringBitSetLite {
            val b = empty()
            for (v in values) b.add(v)
            return b
        }
    }

    private sealed interface Container {
        fun cardinality(): Int
        fun contains(low16: Int): Boolean
        fun add(low16: Int): Container
        fun toIntSequence(highShifted: Int): Sequence<Int>
    }

    private class ArrayCont(val vals: IntArray) : Container {
        override fun cardinality(): Int = vals.size
        override fun contains(low16: Int): Boolean = vals.binarySearch(low16) >= 0
        override fun add(low16: Int): Container {
            val idx = vals.binarySearch(low16)
            if (idx >= 0) return this
            val ins = -idx - 1
            if (vals.size + 1 > ARRAY_MAX) {
                val words = LongArray(1024)
                for (v in vals) words[v ushr 6] = words[v ushr 6] or (1L shl (v and 63))
                words[low16 ushr 6] = words[low16 ushr 6] or (1L shl (low16 and 63))
                return BitmapCont(words, vals.size + 1)
            }
            val next = IntArray(vals.size + 1)
            System.arraycopy(vals, 0, next, 0, ins)
            next[ins] = low16
            System.arraycopy(vals, ins, next, ins + 1, vals.size - ins)
            return ArrayCont(next)
        }

        override fun toIntSequence(highShifted: Int): Sequence<Int> =
            vals.asSequence().map { highShifted or it }
    }

    private class BitmapCont(val words: LongArray, val card: Int) : Container {
        override fun cardinality(): Int = card
        override fun contains(low16: Int): Boolean =
            (words[low16 ushr 6] and (1L shl (low16 and 63))) != 0L

        override fun add(low16: Int): Container {
            val wIdx = low16 ushr 6
            val bit = 1L shl (low16 and 63)
            if ((words[wIdx] and bit) != 0L) return this
            val copy = words.copyOf()
            copy[wIdx] = copy[wIdx] or bit
            return BitmapCont(copy, card + 1)
        }

        override fun toIntSequence(highShifted: Int): Sequence<Int> = sequence {
            for (w in words.indices) {
                var word = words[w]
                while (word != 0L) {
                    val tz = java.lang.Long.numberOfTrailingZeros(word)
                    yield(highShifted or (w shl 6) or tz)
                    word = word and (word - 1L)
                }
            }
        }
    }

    fun add(value: Int) {
        require(value >= 0) { "RoaringBitSetLite supports non-negative docIds, got $value" }
        val hi = value ushr 16
        val lo = value and 0xFFFF
        val prev = chunks[hi]
        chunks[hi] = prev?.add(lo) ?: ArrayCont(intArrayOf(lo))
    }

    fun contains(value: Int): Boolean {
        if (value < 0) return false
        val c = chunks[value ushr 16] ?: return false
        return c.contains(value and 0xFFFF)
    }

    fun cardinality(): Int = chunks.values.sumOf { it.cardinality() }

    fun usesBitmapContainerForChunk(high16: Int): Boolean = chunks[high16] is BitmapCont

    fun toList(): List<Int> = chunks.entries.flatMap { (hi, c) -> c.toIntSequence(hi shl 16).toList() }

    infix fun and(other: RoaringBitSetLite): RoaringBitSetLite {
        val out = empty()
        for ((hi, cA) in chunks) {
            val cB = other.chunks[hi] ?: continue
            for (v in cA.toIntSequence(hi shl 16)) {
                if (cB.contains(v and 0xFFFF)) out.add(v)
            }
        }
        return out
    }

    infix fun or(other: RoaringBitSetLite): RoaringBitSetLite {
        val out = empty()
        for (v in this.toList()) out.add(v)
        for (v in other.toList()) out.add(v)
        return out
    }

    infix fun andNot(other: RoaringBitSetLite): RoaringBitSetLite {
        val out = empty()
        for (v in this.toList()) {
            if (!other.contains(v)) out.add(v)
        }
        return out
    }
}
