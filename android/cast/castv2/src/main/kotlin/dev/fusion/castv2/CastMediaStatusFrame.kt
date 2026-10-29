// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * CastV2 **`MEDIA_STATUS` / `RECEIVER_STATUS` State-Machine & `requestId` Reconciler**.
 */
object CastMediaStatusFrame {
    const val SCOPE =
        "CastV2 MEDIA_STATUS playback state machine, monotonic mediaSessionId/requestId reconciler, and clamped volume/seek controller."

    enum class PlayerState { IDLE, BUFFERING, PLAYING, PAUSED }

    data class SessionSnapshot(
        val mediaSessionId: Long = 0L,
        val lastRequestId: Long = 0L,
        val playerState: PlayerState = PlayerState.IDLE,
        val currentTimeSec: Double = 0.0,
        val durationSec: Double = 0.0,
        val volumeLevel: Double = 1.0,
        val muted: Boolean = false,
    )

    data class StatusUpdate(
        val mediaSessionId: Long,
        val requestId: Long,
        val playerState: PlayerState,
        val currentTimeSec: Double,
        val durationSec: Double,
        val volumeLevel: Double,
        val muted: Boolean,
    )

    fun reconcile(current: SessionSnapshot, incoming: StatusUpdate): SessionSnapshot {
        // Ignore stale out-of-order updates for an older session or older requestId within the same session.
        if (incoming.mediaSessionId < current.mediaSessionId) return current
        if (incoming.mediaSessionId == current.mediaSessionId && incoming.requestId != 0L && incoming.requestId < current.lastRequestId) {
            return current
        }
        val dur = incoming.durationSec.coerceAtLeast(0.0)
        val pos = if (dur > 0.0) incoming.currentTimeSec.coerceIn(0.0, dur) else incoming.currentTimeSec.coerceAtLeast(0.0)
        val vol = incoming.volumeLevel.coerceIn(0.0, 1.0)
        return SessionSnapshot(
            mediaSessionId = incoming.mediaSessionId,
            lastRequestId = maxOf(current.lastRequestId, incoming.requestId),
            playerState = incoming.playerState,
            currentTimeSec = pos,
            durationSec = dur,
            volumeLevel = vol,
            muted = incoming.muted,
        )
    }
}
