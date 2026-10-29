// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * Clean-room Screen Recording Bitrate & Storage Runway Safety Planner (`MD-03`).
 *
 * Computes target H.264 (`video/avc`) or HEVC (`video/hevc`) encoder bitrate from capture
 * resolution + frame rate, enforces a mandatory free-disk reserve (`minSafetyReserveBytes = 256 MiB`)
 * so recording NEVER wedges app-private storage, and estimates max safe recording duration!
 */
object CaptureBitrateBudgetPlanner {
    const val DEFAULT_SAFETY_RESERVE_BYTES = 256L * 1024L * 1024L

    enum class VideoCodec(val mime: String, val bitsPerPixelPerFrame: Double) {
        AVC_H264("video/avc", 0.095),
        HEVC_H265("video/hevc", 0.062),
    }

    data class CaptureBudgetPlan(
        val widthPx: Int,
        val heightPx: Int,
        val fps: Int,
        val codec: VideoCodec,
        val targetBitrateBps: Int,
        val usableStorageBytes: Long,
        val estimatedMaxSeconds: Long,
        val canStartRecording: Boolean,
        val statusSummary: String,
    )

    fun planSession(
        widthPx: Int,
        heightPx: Int,
        fps: Int = 30,
        codec: VideoCodec = VideoCodec.AVC_H264,
        availableBytes: Long,
        safetyReserveBytes: Long = DEFAULT_SAFETY_RESERVE_BYTES,
    ): CaptureBudgetPlan {
        require(widthPx in 320..3840 && heightPx in 240..2160 && fps in 15..60) {
            "capture geometry/fps outside supported bounds"
        }
        // Even dimensions required by hardware MediaCodec YUV420 encoders!
        val evenW = widthPx - (widthPx % 2)
        val evenH = heightPx - (heightPx % 2)
        val rawBps = evenW.toDouble() * evenH.toDouble() * fps.toDouble() * codec.bitsPerPixelPerFrame
        val clampedBps = rawBps.toInt().coerceIn(1_000_000, 24_000_000)

        val usable = (availableBytes - safetyReserveBytes).coerceAtLeast(0L)
        val bytesPerSec = (clampedBps / 8).coerceAtLeast(1)
        val maxSec = usable / bytesPerSec
        val ok = maxSec >= 5L

        val msg = if (ok) {
            "${evenW}x$evenH @ ${fps}fps (${codec.name}, ${"%.1f".format(java.util.Locale.US, clampedBps / 1e6)} Mbps) • ~${maxSec / 60}m ${maxSec % 60}s runway"
        } else {
            "Insufficient free storage above ${safetyReserveBytes / (1024 * 1024)} MiB safety reserve."
        }
        return CaptureBudgetPlan(evenW, evenH, fps, codec, clampedBps, usable, maxSec, ok, msg)
    }
}
