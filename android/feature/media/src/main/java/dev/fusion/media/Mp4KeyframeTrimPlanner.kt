// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * Clean-room MP4 / ISO-BMFF `stss` Sync-Sample Keyframe Trim & Lossless Stream-Copy Planner (`MD-08`).
 */
object Mp4KeyframeTrimPlanner {
    const val SCOPE =
        "Pure-JVM ISO-BMFF keyframe-accurate video trim planner computing lossless stream-copy vs GOP lead-in hardware re-encode segments (MD-08)."

    data class TrimSegmentPlan(
        val reencodeLeadInRangeMs: LongRange?,
        val losslessStreamCopyRangeMs: LongRange?,
        val nearestStartKeyframeMs: Long,
        val outputDurationMs: Long,
    )

    fun planTrim(
        sortedKeyframesMs: List<Long>,
        totalDurationMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        requireFrameExactCut: Boolean = true,
    ): TrimSegmentPlan {
        require(sortedKeyframesMs.isNotEmpty() && sortedKeyframesMs.first() == 0L) { "keyframes must start at 0ms" }
        require(trimStartMs >= 0L && trimEndMs > trimStartMs && trimEndMs <= totalDurationMs) {
            "invalid trim range [$trimStartMs, $trimEndMs] within $totalDurationMs"
        }
        val prevKf = sortedKeyframesMs.last { it <= trimStartMs }
        val nextKf = sortedKeyframesMs.firstOrNull { it >= trimStartMs }
        if (!requireFrameExactCut || prevKf == trimStartMs) {
            val effStart = if (requireFrameExactCut) trimStartMs else prevKf
            return TrimSegmentPlan(null, effStart..trimEndMs, prevKf, trimEndMs - effStart)
        }
        return if (nextKf == null || nextKf >= trimEndMs) {
            TrimSegmentPlan(trimStartMs..trimEndMs, null, prevKf, trimEndMs - trimStartMs)
        } else {
            TrimSegmentPlan(trimStartMs until nextKf, nextKf..trimEndMs, prevKf, trimEndMs - trimStartMs)
        }
    }
}
