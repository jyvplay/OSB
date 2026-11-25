// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

/**
 * Bounded monotonic RTSP CSeq sequence tracker.
 */
class AirPlayRtspCSeqTracker private constructor() {
    companion object {
        fun create(): AirPlayRtspCSeqTracker = AirPlayRtspCSeqTracker()
    }

    private var lastCSeq = 0

    fun accept(cseq: Int): Boolean {
        require(cseq >= 1) { "CSeq must be >= 1" }
        if (cseq <= lastCSeq) return false
        lastCSeq = cseq
        return true
    }

    fun current(): Int = lastCSeq
}
