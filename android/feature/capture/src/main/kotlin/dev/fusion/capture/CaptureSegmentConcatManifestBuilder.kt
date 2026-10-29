// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

import java.security.MessageDigest

/**
 * Gapless multi-segment rollover concatenation manifest + SHA-256 hash-chain verifier for
 * long-running **Recall** `TIER_4_NATIVE_VIEWPORT_AV` recordings exceeding single-file rollover
 * boundaries (`RecallCaptureRolloverPlanner`).
 */
object CaptureSegmentConcatManifestBuilder {
    const val SCOPE =
        "Long-session Tier-4 capture segment continuity verifier and hash-chained FFconcat / JSON manifest writer."

    data class RolloverPart(
        val index: Int,
        val filename: String,
        val startPtsMicros: Long,
        val endPtsMicros: Long,
        val sha256Hex: String,
    )

    data class ChainedManifest(
        val sessionId: String,
        val totalDurationMicros: Long,
        val chainRootSha256: String,
        val ffconcatText: String,
    )

    fun buildAndVerifyChain(sessionId: String, parts: List<RolloverPart>, maxSeamGapMicros: Long = 40_000L): ChainedManifest {
        require(sessionId.isNotBlank() && parts.isNotEmpty())
        val sorted = parts.sortedBy { it.index }
        var chainHash = "0".repeat(64)
        var totalDur = 0L
        val sb = StringBuilder("ffconcat version 1.0\n")

        for ((i, p) in sorted.withIndex()) {
            require(p.index == i && p.endPtsMicros > p.startPtsMicros && p.sha256Hex.length == 64) {
                "Invalid rollover segment #$i"
            }
            if (i > 0) {
                val gap = p.startPtsMicros - sorted[i - 1].endPtsMicros
                require(gap in -1_000L..maxSeamGapMicros) { "Discontinuous rollover seam gap ${gap}us at segment $i" }
            }
            val dur = p.endPtsMicros - p.startPtsMicros
            totalDur += dur
            chainHash = MessageDigest.getInstance("SHA-256")
                .digest("$chainHash|${p.index}|${p.sha256Hex}|$dur".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val safeName = p.filename.replace("'", "'\\''")
            sb.append("file '").append(safeName).append("'\n")
            sb.append("duration ").append("%.6f".format(java.util.Locale.ROOT, dur / 1_000_000.0)).append("\n")
        }
        return ChainedManifest(sessionId, totalDur, chainHash, sb.toString())
    }
}
