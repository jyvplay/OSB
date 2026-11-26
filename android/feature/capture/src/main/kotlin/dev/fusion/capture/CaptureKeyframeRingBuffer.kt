// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * Production-grade circular ring-buffer & keyframe-aligned GOP segmenter.
 */
class CaptureKeyframeRingBuffer private constructor(private val maxFrames: Int) {
    companion object {
        data class EncodedFrame(val ptsUs: Long, val isKeyframe: Boolean, val sizeBytes: Int)

        fun create(maxFrames: Int = 300): CaptureKeyframeRingBuffer {
            require(maxFrames in 1..10_000) { "maxFrames out of bounds" }
            return CaptureKeyframeRingBuffer(maxFrames)
        }
    }

    private val ring = ArrayDeque<EncodedFrame>()

    fun push(frame: EncodedFrame) {
        if (ring.size == maxFrames) ring.removeFirst()
        ring.addLast(frame)
    }

    fun extractFromLastKeyframe(): List<EncodedFrame> {
        val list = ring.toList()
        val idx = list.indexOfLast { it.isKeyframe }
        return if (idx < 0) emptyList() else list.subList(idx, list.size)
    }
}
