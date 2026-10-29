// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room **Slotted-Page B-Tree Leaf Page Binary Codec** (`4096`-byte page layout).
 *
 * Implements the classic database slotted page:
 * - `[0..7]` 8-byte header (`magic = 0x4254` `"BT"`, `keyCount: u16`, `cellContentStart: u16`, `flags: u16`);
 * - `[8 .. 8 + 2*keyCount)` sorted `u16` cell-offset pointer array growing **upward**;
 * - `[cellContentStart .. pageSize)` variable-length `[keyLen: u16][valLen: u16][key][val]` cells
 *   growing **downward** from the end of the `4096`-byte page;
 * - Supports `O(log N)` binary-search `lookup(page, key)`, full serialization, and median split-key
 *   selection when a page fills!
 */
object BTreePageLayout {
    const val SCOPE =
        "Clean-room 4096-byte slotted-page B-Tree leaf binary codec with O(log N) binary-search lookup and median split planner."

    const val DEFAULT_PAGE_SIZE = 4_096
    private const val MAGIC_BT: Short = 0x4254

    data class SplitPlan(
        val medianKey: ByteArray,
        val leftEntries: List<Pair<ByteArray, ByteArray>>,
        val rightEntries: List<Pair<ByteArray, ByteArray>>,
    )

    fun packLeafPage(entries: List<Pair<ByteArray, ByteArray>>, pageSize: Int = DEFAULT_PAGE_SIZE): ByteArray {
        require(pageSize in 256..65_536) { "pageSize outside 256..65536" }
        val sorted = entries.sortedWith { a, b -> compareLex(a.first, b.first) }
        for (i in 1 until sorted.size) {
            require(compareLex(sorted[i - 1].first, sorted[i].first) < 0) { "duplicate B-Tree key in leaf page" }
        }
        val page = ByteArray(pageSize)
        val buf = ByteBuffer.wrap(page).order(ByteOrder.BIG_ENDIAN)
        var contentTop = pageSize
        val ptrBase = 8
        val ptrEnd = ptrBase + sorted.size * 2
        require(ptrEnd <= contentTop) { "slotted page pointer overflow" }

        sorted.forEachIndexed { idx, (k, v) ->
            val cellSize = 4 + k.size + v.size
            contentTop -= cellSize
            require(contentTop >= ptrEnd) { "slotted B-Tree leaf page overflow ($pageSize B)" }
            buf.putShort(ptrBase + idx * 2, contentTop.toShort())
            buf.putShort(contentTop, k.size.toShort())
            buf.putShort(contentTop + 2, v.size.toShort())
            System.arraycopy(k, 0, page, contentTop + 4, k.size)
            System.arraycopy(v, 0, page, contentTop + 4 + k.size, v.size)
        }
        buf.putShort(0, MAGIC_BT)
        buf.putShort(2, sorted.size.toShort())
        buf.putShort(4, contentTop.toShort())
        buf.putShort(6, 0)
        return page
    }

    fun lookup(page: ByteArray, targetKey: ByteArray): ByteArray? {
        require(page.size >= 8) { "truncated B-Tree page" }
        val buf = ByteBuffer.wrap(page).order(ByteOrder.BIG_ENDIAN)
        require(buf.getShort(0) == MAGIC_BT) { "invalid B-Tree slotted page magic" }
        val count = buf.getShort(2).toInt() and 0xFFFF
        var lo = 0
        var hi = count - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cellOff = buf.getShort(8 + mid * 2).toInt() and 0xFFFF
            val kLen = buf.getShort(cellOff).toInt() and 0xFFFF
            val vLen = buf.getShort(cellOff + 2).toInt() and 0xFFFF
            val cmp = compareLexSlice(page, cellOff + 4, kLen, targetKey)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return page.copyOfRange(cellOff + 4 + kLen, cellOff + 4 + kLen + vLen)
            }
        }
        return null
    }

    fun planSplit(entries: List<Pair<ByteArray, ByteArray>>): SplitPlan {
        require(entries.size >= 2) { "cannot split fewer than 2 entries" }
        val sorted = entries.sortedWith { a, b -> compareLex(a.first, b.first) }
        val mid = sorted.size / 2
        return SplitPlan(
            medianKey = sorted[mid].first,
            leftEntries = sorted.subList(0, mid),
            rightEntries = sorted.subList(mid, sorted.size),
        )
    }

    fun compareLex(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val ai = a[i].toInt() and 0xFF
            val bi = b[i].toInt() and 0xFF
            if (ai != bi) return ai.compareTo(bi)
        }
        return a.size.compareTo(b.size)
    }

    private fun compareLexSlice(page: ByteArray, start: Int, len: Int, b: ByteArray): Int {
        val n = minOf(len, b.size)
        for (i in 0 until n) {
            val ai = page[start + i].toInt() and 0xFF
            val bi = b[i].toInt() and 0xFF
            if (ai != bi) return ai.compareTo(bi)
        }
        return len.compareTo(b.size)
    }
}
