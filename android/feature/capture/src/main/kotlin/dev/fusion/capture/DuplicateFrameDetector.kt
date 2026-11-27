// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * R146 — near-duplicate frame run detector for the capture pipeline
 * (feeds segment quotas and the rollover planner; pairs with
 * [RecallCaptureRolloverPlanner], which owns segment budgeting).
 *
 * Each frame carries a timestamp and a perceptual fingerprint: a 9x8 grid of
 * 4-bit luma values (72 hex characters, row-major). The 64-bit dHash is the
 * usual horizontal comparison (`grid[r][c+1] > grid[r][c]`) across the eight
 * columns. A frame joins the open run when its dHash is within
 * [NEAR_BITS] bit flips of the run's **anchor** (its first frame) and its
 * timestamp is within [MAX_SEG_MS] of the run start. Runs of at least
 * [MIN_RUN] frames are emitted `startMs:endMs:count`; singleton frames are
 * never emitted, so a wholly unique capture reports `none`.
 *
 * Declared narrowings: 1..[MAX_FRAMES] frames; strictly increasing
 * timestamps; fingerprints exactly 72 lowercase hex characters; distance is
 * plain Hamming popcount over 64 bits (no weighted metrics).
 */
object DuplicateFrameDetector {
    const val SCOPE =
        "Anchor-based dHash near-duplicate run detection over bounded frame series; no decode, no encode, no pixels retained."

    const val MAX_FRAMES = 4096
    const val NEAR_BITS = 2
    const val MAX_SEG_MS = 5_000L
    const val MIN_RUN = 2
    private val HASH_RE = Regex("[0-9a-f]{72}")

    data class Frame(val epochMs: Long, val hash: Long) // hash: 64-bit dHash

    fun parseFingerprint(hex: String): Long {
        require(HASH_RE.matches(hex)) { "fingerprint must be 72 lowercase hex chars" }
        // 72 nibbles -> 9 columns x 8 rows of 4-bit values.
        val cells = IntArray(72)
        for (i in 0 until 72) {
            cells[i] = Character.digit(hex[i], 16)
        }
        var dHash = 0L
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val left = cells[r * 9 + c]
                val right = cells[r * 9 + c + 1]
                dHash = dHash shl 1
                if (right > left) dHash = dHash or 1L
            }
        }
        return dHash
    }

    private fun popcount(x: Long): Int {
        var v = x
        v = v - ((v ushr 1) and 0x5555555555555555UL.toLong())
        v = (v and 0x3333333333333333UL.toLong()) + ((v ushr 2) and 0x3333333333333333UL.toLong())
        v = (v + (v ushr 4)) and 0x0f0f0f0f0f0f0f0fUL.toLong()
        return ((v * 0x0101010101010101UL.toLong()) ushr 56).toInt()
    }

    fun parse(series: String): List<Frame> {
        require(series.isNotEmpty()) { "empty frame series" }
        val tokens = series.split(',')
        require(tokens.size in 1..MAX_FRAMES) { "frame count out of 1..$MAX_FRAMES" }
        val frames = tokens.map { token ->
            val colon = token.indexOf(':')
            require(colon > 0 && colon == token.lastIndexOf(':')) {
                "frame must be epochMs:hash72"
            }
            val ts = token.substring(0, colon).toLongOrNull()
                ?: throw IllegalArgumentException("frame timestamp not an int")
            require(ts >= 0) { "frame timestamp must be >= 0" }
            Frame(ts, parseFingerprint(token.substring(colon + 1)))
        }
        for (i in 1 until frames.size) {
            require(frames[i].epochMs > frames[i - 1].epochMs) {
                "frame timestamps must be strictly increasing"
            }
        }
        return frames
    }

    fun detect(series: String): String {
        val frames = parse(series)
        val out = ArrayList<String>()
        var i = 0
        while (i < frames.size) {
            val anchorHash = frames[i].hash
            val anchorTs = frames[i].epochMs
            var j = i
            while (j + 1 < frames.size &&
                frames[j + 1].epochMs - anchorTs <= MAX_SEG_MS &&
                popcount(frames[j + 1].hash xor anchorHash) <= NEAR_BITS
            ) {
                j++
            }
            val count = j - i + 1
            if (count >= MIN_RUN) {
                out.add("${frames[i].epochMs}:${frames[j].epochMs}:$count")
            }
            i = j + 1
        }
        return if (out.isEmpty()) "none" else out.joinToString(",")
    }
}
