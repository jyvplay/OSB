// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

/**
 * Bounded WAL checkpoint watermark tracker.
 */
class WalCheckpointWatermark private constructor(private val thresholdFrames: Int) {
    companion object {
        const val MAX_THRESHOLD = 100_000

        fun create(thresholdFrames: Int): WalCheckpointWatermark {
            require(thresholdFrames in 1..MAX_THRESHOLD) { "threshold out of bounds" }
            return WalCheckpointWatermark(thresholdFrames)
        }
    }

    private var uncheckpointed = 0

    fun appendFrames(count: Int): Boolean {
        require(count >= 0) { "negative frame count" }
        uncheckpointed += count
        return uncheckpointed >= thresholdFrames
    }

    fun markCheckpointed() {
        uncheckpointed = 0
    }

    fun pendingFrames(): Int = uncheckpointed
}
