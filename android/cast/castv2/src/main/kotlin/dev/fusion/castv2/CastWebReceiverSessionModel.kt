// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * Clean-room Google Cast `urn:x-cast:com.google.cast.media` Web Receiver Session State Machine &
 * Adaptive WebRTC Window-Mirror Bitrate Controller (`CA-09`).
 */
object CastWebReceiverSessionModel {
    const val MEDIA_NAMESPACE = "urn:x-cast:com.google.cast.media"

    enum class PlayerState { IDLE, BUFFERING, PLAYING, PAUSED }

    data class ReceiverMediaStatus(
        val mediaSessionId: Int,
        val state: PlayerState,
        val currentTimeSeconds: Double,
        val playbackRate: Double = 1.0,
        val volumeLevel: Double = 1.0,
    )

    fun buildLoadCommandJson(
        requestId: Int,
        contentHttpsUrl: String,
        contentType: String,
        startTimeSeconds: Double = 0.0,
    ): String {
        require(requestId > 0) { "requestId must be > 0" }
        require(contentHttpsUrl.startsWith("https://") || contentHttpsUrl.startsWith("http://192.168.") || contentHttpsUrl.startsWith("http://10.")) {
            "Cast LOAD contentId must be HTTPS or LAN HTTP"
        }
        return """{"requestId":$requestId,"type":"LOAD","autoplay":true,"currentTime":${"%.3f".format(java.util.Locale.US, startTimeSeconds.coerceAtLeast(0.0))},"media":{"contentId":"${esc(contentHttpsUrl)}","streamType":"BUFFERED","contentType":"${esc(contentType)}"}}"""
    }

    fun applyMediaCommand(
        current: ReceiverMediaStatus,
        commandType: String,
        seekSecondsOrNull: Double? = null,
    ): ReceiverMediaStatus = when (commandType.uppercase()) {
        "PLAY" -> current.copy(state = PlayerState.PLAYING)
        "PAUSE" -> current.copy(state = PlayerState.PAUSED)
        "STOP" -> current.copy(state = PlayerState.IDLE, currentTimeSeconds = 0.0)
        "SEEK" -> current.copy(currentTimeSeconds = (seekSecondsOrNull ?: current.currentTimeSeconds).coerceAtLeast(0.0))
        else -> throw IllegalArgumentException("unsupported Cast media command '$commandType'")
    }

    /** AIMD (Additive-Increase / Multiplicative-Decrease) bitrate controller for WebRTC window mirror (`CA-09`). */
    fun adaptWebRtcMirrorBitrateKbps(
        currentKbps: Int,
        packetLossFraction: Double,
        rttMs: Int,
        minKbps: Int = 600,
        maxKbps: Int = 12_000,
    ): Int {
        val next = when {
            packetLossFraction >= 0.08 || rttMs >= 220 -> (currentKbps * 0.72).toInt()
            packetLossFraction <= 0.01 && rttMs <= 60 -> (currentKbps * 1.08 + 150).toInt()
            else -> currentKbps
        }
        return next.coerceIn(minKbps, maxKbps)
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
