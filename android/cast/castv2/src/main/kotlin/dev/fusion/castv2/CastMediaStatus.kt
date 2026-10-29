// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * Clean-room OpenReceiver / CastV2 `MEDIA_STATUS` deterministic playback state reducer (`CastMediaStatus`).
 *
 * Enforces monotonic `requestId` deduplication (rejecting stale/reordered control commands), bounded
 * `[0.0 .. 1.0]` volume & `[0.25 .. 4.0]` playback rate, and accurate extrapolated playhead calculation!
 */
object CastMediaStatus {
    const val SCOPE =
        "Deterministic OpenReceiver MEDIA_STATUS playback reducer (monotonic requestId deduplication, play/pause/seek/volume/rate clamping, extrapolated playhead); TLS socket framing out of scope."

    enum class PlayerState { IDLE, BUFFERING, PLAYING, PAUSED }
    enum class IdleReason { NONE, FINISHED, CANCELLED, ERROR }

    data class StatusSnapshot(
        val mediaSessionId: Long = 1L,
        val lastRequestId: Long = 0L,
        val playerState: PlayerState = PlayerState.IDLE,
        val idleReason: IdleReason = IdleReason.NONE,
        val contentUri: String? = null,
        val durationMs: Long = 0L,
        val basePositionMs: Long = 0L,
        val baseTimestampMs: Long = 0L,
        val playbackRate: Double = 1.0,
        val volumeLevel: Double = 1.0,
        val muted: Boolean = false,
    ) {
        fun currentPositionMs(nowMs: Long): Long {
            if (playerState != PlayerState.PLAYING) return basePositionMs
            val elapsed = (nowMs - baseTimestampMs).coerceAtLeast(0L)
            val advanced = basePositionMs + (elapsed.toDouble() * playbackRate).toLong()
            return if (durationMs > 0L) advanced.coerceIn(0L, durationMs) else advanced.coerceAtLeast(0L)
        }
    }

    sealed interface Command {
        val requestId: Long
        data class Load(override val requestId: Long, val uri: String, val durationMs: Long, val autoplay: Boolean = true) : Command
        data class Play(override val requestId: Long) : Command
        data class Pause(override val requestId: Long) : Command
        data class Seek(override val requestId: Long, val positionMs: Long) : Command
        data class SetVolume(override val requestId: Long, val level: Double?, val muted: Boolean?) : Command
        data class SetPlaybackRate(override val requestId: Long, val rate: Double) : Command
        data class Stop(override val requestId: Long) : Command
    }

    fun reduce(state: StatusSnapshot, cmd: Command, nowMs: Long): StatusSnapshot {
        // Deduplicate / reject replayed or out-of-order requestIds.
        if (cmd.requestId <= state.lastRequestId) return state

        val syncedPos = state.currentPositionMs(nowMs)
        return when (cmd) {
            is Command.Load -> {
                require(cmd.uri.startsWith("https://") || cmd.uri.startsWith("http://")) {
                    "Unsupported OpenReceiver content URI scheme: ${cmd.uri}"
                }
                require(cmd.durationMs >= 0L) { "Negative durationMs" }
                state.copy(
                    mediaSessionId = state.mediaSessionId + 1L,
                    lastRequestId = cmd.requestId,
                    playerState = if (cmd.autoplay) PlayerState.PLAYING else PlayerState.PAUSED,
                    idleReason = IdleReason.NONE,
                    contentUri = cmd.uri,
                    durationMs = cmd.durationMs,
                    basePositionMs = 0L,
                    baseTimestampMs = nowMs,
                )
            }
            is Command.Play -> if (state.contentUri == null) state else state.copy(
                lastRequestId = cmd.requestId,
                playerState = PlayerState.PLAYING,
                idleReason = IdleReason.NONE,
                basePositionMs = syncedPos,
                baseTimestampMs = nowMs,
            )
            is Command.Pause -> if (state.contentUri == null) state else state.copy(
                lastRequestId = cmd.requestId,
                playerState = PlayerState.PAUSED,
                basePositionMs = syncedPos,
                baseTimestampMs = nowMs,
            )
            is Command.Seek -> if (state.contentUri == null) state else {
                val clamped = if (state.durationMs > 0L) cmd.positionMs.coerceIn(0L, state.durationMs)
                else cmd.positionMs.coerceAtLeast(0L)
                state.copy(
                    lastRequestId = cmd.requestId,
                    basePositionMs = clamped,
                    baseTimestampMs = nowMs,
                )
            }
            is Command.SetVolume -> {
                val lvl = cmd.level?.let {
                    require(it.isFinite()) { "Non-finite volume" }
                    it.coerceIn(0.0, 1.0)
                } ?: state.volumeLevel
                state.copy(
                    lastRequestId = cmd.requestId,
                    volumeLevel = lvl,
                    muted = cmd.muted ?: state.muted,
                )
            }
            is Command.SetPlaybackRate -> {
                require(cmd.rate.isFinite()) { "Non-finite playbackRate" }
                state.copy(
                    lastRequestId = cmd.requestId,
                    basePositionMs = syncedPos,
                    baseTimestampMs = nowMs,
                    playbackRate = cmd.rate.coerceIn(0.25, 4.0),
                )
            }
            is Command.Stop -> state.copy(
                lastRequestId = cmd.requestId,
                playerState = PlayerState.IDLE,
                idleReason = IdleReason.CANCELLED,
                contentUri = null,
                basePositionMs = 0L,
                baseTimestampMs = nowMs,
            )
        }
    }
}
