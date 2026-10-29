// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * Pure-JVM **Recall Capture Storage Headroom & Segmented MP4 Auto-Rollover Planner**
 * for `:feature:capture`, ensuring long Tier-4 native viewport recordings split cleanly at
 * keyframe boundaries (`<= 1.9 GiB` FAT32/SAF compatibility + mandatory `512 MiB` emergency free-disk floor!).
 */
object RecallCaptureRolloverPlanner {
    const val SCOPE =
        "Recall Tier-4 long-recording MP4 segment rollover planner enforcing 512 MiB free-space reserve and keyframe-aligned file splits."

    const val EMERGENCY_FREE_FLOOR_BYTES = 512L * 1024L * 1024L
    const val DEFAULT_MAX_SEGMENT_BYTES = 1_900_000_000L

    enum class RolloverAction { CONTINUE_CURRENT_SEGMENT, SPLIT_AT_NEXT_KEYFRAME, EMERGENCY_FINALIZE_LOW_DISK }

    fun evaluateTick(
        currentSegmentBytes: Long,
        availableDiskFreeBytes: Long,
        nextFrameIsKeyframe: Boolean,
        maxSegmentBytes: Long = DEFAULT_MAX_SEGMENT_BYTES,
    ): RolloverAction {
        if (availableDiskFreeBytes <= EMERGENCY_FREE_FLOOR_BYTES) {
            return RolloverAction.EMERGENCY_FINALIZE_LOW_DISK
        }
        if (currentSegmentBytes >= maxSegmentBytes && nextFrameIsKeyframe) {
            return RolloverAction.SPLIT_AT_NEXT_KEYFRAME
        }
        return RolloverAction.CONTINUE_CURRENT_SEGMENT
    }
}
