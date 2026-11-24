// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * Bounded capture segment quota tracker. Tracks recorded capture segment
 * sizes against a total quota, refusing segments that would exceed it.
 * Supports segment registration, size queries, and quota reset. Does not
 * perform actual recording — the capture engine handles that.
 *
 * Declared narrowings: at most [MAX_SEGMENTS] segments; segment IDs at most
 * [MAX_ID_LEN] chars; segment sizes non-negative and at most
 * [MAX_SEGMENT_BYTES]; total quota non-negative and at most [MAX_QUOTA_BYTES];
 * segment IDs matching [ID_RE] only.
 */
class CaptureSegmentQuota private constructor(
    private val quotaBytes: Long,
    private val segments: LinkedHashMap<String, Long>
) {
    companion object {
        const val MAX_SEGMENTS = 4096
        const val MAX_ID_LEN = 128
        const val MAX_SEGMENT_BYTES = 1L << 30 // 1 GiB
        const val MAX_QUOTA_BYTES = 1L << 40 // 1 TiB
        private val ID_RE = Regex("[a-zA-Z0-9_-]{1,64}")

        fun empty(quotaBytes: Long): CaptureSegmentQuota {
            require(quotaBytes >= 0 && quotaBytes <= MAX_QUOTA_BYTES) {
                "quota out of bounds"
            }
            return CaptureSegmentQuota(quotaBytes, LinkedHashMap())
        }
    }

    fun registerSegment(segmentId: String, sizeBytes: Long): Boolean {
        require(ID_RE.matches(segmentId)) { "invalid segment id: $segmentId" }
        require(segmentId.length <= MAX_ID_LEN) { "segment id too long" }
        require(sizeBytes >= 0 && sizeBytes <= MAX_SEGMENT_BYTES) {
            "segment size out of bounds"
        }
        require(segments.size < MAX_SEGMENTS || segments.containsKey(segmentId)) {
            "too many segments: $MAX_SEGMENTS"
        }
        val currentTotal = segments.values.sum()
        if (currentTotal + sizeBytes > quotaBytes) {
            return false // would exceed quota
        }
        segments[segmentId] = sizeBytes
        return true
    }

    fun removeSegment(segmentId: String): Boolean = segments.remove(segmentId) != null

    fun usedBytes(): Long = segments.values.sum()
    fun remainingBytes(): Long = quotaBytes - usedBytes()
    fun segmentCount(): Int = segments.size
    fun isQuotaExceeded(): Boolean = usedBytes() > quotaBytes
    fun clear() = segments.clear()
}
