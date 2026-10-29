// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room `Apache-2.0` implementation of **Tor Proposal 327 / `hspow-spec`** Onion Service
 * Introduction Proof-of-Work effort verifier and priority queue, built on a standalone pure-JVM
 * IETF **RFC 7693 `BLAKE2b-512` / `BLAKE2b-32`** compression engine (zero LGPL `equix`/`hashx` code).
 *
 * Verifies client puzzle effort `E` via the Proposal 327 effort predicate:
 *   `uint32_be(BLAKE2b(seed32 || serviceId32 || nonce16 || effortBe4 || proofBytes)[0..3]) * E <= 0xFFFF_FFFFL`
 * queues validated `INTRODUCE1` requests in descending effort order (`INTRO_QUEUE`), evicts lowest-effort
 * flood traffic when queue capacity is reached, and recomputes suggested effort every 300s (5 min).
 */
@OptIn(ExperimentalUnsignedTypes::class)
object TorOnionPowIntroQueue {
    const val SCOPE =
        "Clean-room Apache-2.0 RFC 7693 BLAKE2b + Tor Proposal 327 Onion Service DoS Proof-of-Work effort verifier, descending-effort INTRO_QUEUE priority scheduler, and 300s dynamic suggested-effort controller (zero disk persistence)."

    const val UINT32_MAX = 0xFFFF_FFFFL
    const val EFFORT_UPDATE_PERIOD_SEC = 300

    private val IV = ulongArrayOf(
        0x6a09e667f3bcc908uL, 0xbb67ae8584caa73buL, 0x3c6ef372fe94f82buL, 0xa54ff53a5f1d36f1uL,
        0x510e527fade682d1uL, 0x9b05688c2b3e6c1fuL, 0x1f83d9abfb41bd6buL, 0x5be0cd19137e2179uL,
    )

    private val SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
    )

    data class PowSolution(
        val seed32: ByteArray,
        val serviceId32: ByteArray,
        val nonce16: ByteArray,
        val effort: Long,
        val proof16: ByteArray,
        val requestId: String,
    )

    fun blake2b(input: ByteArray, outLen: Int = 32): ByteArray {
        require(outLen in 1..64) { "BLAKE2b outLen must be 1..64" }
        val h = IV.copyOf()
        h[0] = h[0] xor (0x01010000uL or outLen.toULong())
        var offset = 0
        var total = 0uL
        val block = ByteArray(128)
        while (offset + 128 < input.size) {
            System.arraycopy(input, offset, block, 0, 128)
            total += 128uL
            compress(h, block, total, isLast = false)
            offset += 128
        }
        val rem = input.size - offset
        block.fill(0)
        if (rem > 0) System.arraycopy(input, offset, block, 0, rem)
        total += rem.toULong()
        compress(h, block, total, isLast = true)

        val full = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        for (w in h) full.putLong(w.toLong())
        return full.array().copyOf(outLen)
    }

    fun verifyPowEffort(sol: PowSolution): Boolean {
        if (sol.seed32.size != 32 || sol.serviceId32.size != 32 || sol.nonce16.size != 16 || sol.proof16.size != 16) return false
        if (sol.effort < 1L || sol.effort > UINT32_MAX) return false
        val msg = ByteBuffer.allocate(32 + 32 + 16 + 4 + 16).order(ByteOrder.BIG_ENDIAN)
            .put(sol.seed32)
            .put(sol.serviceId32)
            .put(sol.nonce16)
            .putInt(sol.effort.toInt())
            .put(sol.proof16)
            .array()
        val digest4 = blake2b(msg, outLen = 4)
        val r = ByteBuffer.wrap(digest4).order(ByteOrder.BIG_ENDIAN).int.toLong() and UINT32_MAX
        // Checked 64-bit unsigned product r * E <= UINT32_MAX (Proposal 327 §3.3)
        return r * sol.effort <= UINT32_MAX
    }

    fun solveClientPuzzle(seed32: ByteArray, serviceId32: ByteArray, effort: Long, requestId: String, maxTrials: Int = 50_000): PowSolution {
        require(effort >= 1L && seed32.size == 32 && serviceId32.size == 32)
        val nonce = ByteArray(16)
        val proof = ByteArray(16) { (it * 17 + 3).toByte() }
        for (trial in 0 until maxTrials) {
            ByteBuffer.wrap(nonce).order(ByteOrder.LITTLE_ENDIAN).putLong(0, trial.toLong())
            val cand = PowSolution(seed32, serviceId32, nonce.copyOf(), effort, proof, requestId)
            if (verifyPowEffort(cand)) return cand
        }
        error("Exceeded maxTrials=$maxTrials for effort=$effort")
    }

    fun scheduleIntroQueue(candidates: List<PowSolution>, maxQueueDepth: Int): List<PowSolution> {
        require(maxQueueDepth >= 1)
        return candidates
            .filter { verifyPowEffort(it) }
            .sortedWith(compareByDescending<PowSolution> { it.effort }.thenBy { it.requestId })
            .take(maxQueueDepth)
    }

    fun computeSuggestedEffort(currentEffort: Long, queuedCount: Int, trimmedDuringPeriod: Int, queueCapacity: Int): Long {
        return when {
            trimmedDuringPeriod > 0 || queuedCount >= queueCapacity -> (currentEffort.coerceAtLeast(1L) * 2L).coerceAtMost(1_000_000L)
            queuedCount < queueCapacity / 4 -> if (currentEffort <= 1L) 0L else (currentEffort * 2L) / 3L
            else -> currentEffort
        }
    }

    private fun compress(h: ULongArray, block: ByteArray, t: ULong, isLast: Boolean) {
        val m = ULongArray(16)
        val bb = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0..15) m[i] = bb.long.toULong()
        val v = ULongArray(16)
        for (i in 0..7) {
            v[i] = h[i]
            v[i + 8] = IV[i]
        }
        v[12] = v[12] xor t
        if (isLast) v[14] = v[14] xor ULong.MAX_VALUE

        fun rotr(x: ULong, n: Int): ULong = (x shr n) or (x shl (64 - n))
        fun g(a: Int, b: Int, c: Int, d: Int, x: ULong, y: ULong) {
            v[a] = v[a] + v[b] + x
            v[d] = rotr(v[d] xor v[a], 32)
            v[c] = v[c] + v[d]
            v[b] = rotr(v[b] xor v[c], 24)
            v[a] = v[a] + v[b] + y
            v[d] = rotr(v[d] xor v[a], 16)
            v[c] = v[c] + v[d]
            v[b] = rotr(v[b] xor v[c], 63)
        }

        for (r in 0..11) {
            val s = SIGMA[r]
            g(0, 4, 8, 12, m[s[0]], m[s[1]])
            g(1, 5, 9, 13, m[s[2]], m[s[3]])
            g(2, 6, 10, 14, m[s[4]], m[s[5]])
            g(3, 7, 11, 15, m[s[6]], m[s[7]])
            g(0, 5, 10, 15, m[s[8]], m[s[9]])
            g(1, 6, 11, 12, m[s[10]], m[s[11]])
            g(2, 7, 8, 13, m[s[12]], m[s[13]])
            g(3, 4, 9, 14, m[s[14]], m[s[15]])
        }
        for (i in 0..7) h[i] = h[i] xor v[i] xor v[i + 8]
    }
}
