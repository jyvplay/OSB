// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

import kotlin.math.ceil

/**
 * Bounded beam transfer ETA estimator in seconds.
 */
class BeamTransferEtaEstimator private constructor() {
    companion object {
        fun estimateSeconds(remainingBytes: Long, bytesPerSec: Long): Long {
            require(remainingBytes >= 0L) { "negative remainingBytes" }
            require(bytesPerSec > 0L) { "bytesPerSec must be positive" }
            if (remainingBytes == 0L) return 0L
            return ceil(remainingBytes.toDouble() / bytesPerSec.toDouble()).toLong()
        }
    }
}
