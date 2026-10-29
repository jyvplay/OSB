// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room AirPlay / RAOP Audio Sender RTP Sync Packetizer (`PT = 84`, 44.1 kHz PCM clock,
 * `CA-01`) & Photo Slideshow HTTP Header Builder (`CA-03`).
 *
 * Explicitly documents and enforces legal/license boundaries:
 * - `CA-04`: FairPlay DRM screen-mirroring is excluded unless legally licensed.
 * - `CA-06` / `CA-07`: GPL AirPlay receiver code is NEVER linked into this Apache-2.0 codebase.
 */
object RaopRtpSyncPacketizer {
    const val RTP_PAYLOAD_TYPE_TIMING_SYNC = 84
    const val SAMPLE_RATE_HZ = 44_100L
    const val SYNC_PACKET_BYTES = 20

    enum class SlideshowTransition(val wireKey: String) {
        NONE("None"),
        DISSOLVE("Dissolve"),
        PUSH_LEFT("PushLeft"),
    }

    data class RaopSyncPacket(
        val extensionFirstAfterFlush: Boolean,
        val currentRtpTimestamp: Long, // uint32 wrapping 44.1kHz sample counter
        val ntpSeconds: Long,          // uint32 NTP seconds since 1900-01-01
        val ntpFraction: Long,         // uint32 NTP fractional seconds
        val nextPacketRtpTimestamp: Long,
    )

    /** Encodes the 20-byte RAOP RTP `PT=84` audio clock-sync control packet. */
    fun encodeSyncPacket(pkt: RaopSyncPacket): ByteArray {
        val buf = ByteBuffer.allocate(SYNC_PACKET_BYTES).order(ByteOrder.BIG_ENDIAN)
        val b0 = if (pkt.extensionFirstAfterFlush) 0x90 else 0x80 // RTP v2 + optional extension marker
        val b1 = 0x80 or RTP_PAYLOAD_TYPE_TIMING_SYNC // Marker bit + PT 84 (0xD4)
        buf.put(b0.toByte())
        buf.put(b1.toByte())
        buf.putShort(0x0007) // Fixed RAOP sync sequence indicator
        buf.putInt((pkt.currentRtpTimestamp and 0xFFFF_FFFFL).toInt())
        buf.putInt((pkt.ntpSeconds and 0xFFFF_FFFFL).toInt())
        buf.putInt((pkt.ntpFraction and 0xFFFF_FFFFL).toInt())
        buf.putInt((pkt.nextPacketRtpTimestamp and 0xFFFF_FFFFL).toInt())
        return buf.array()
    }

    fun decodeSyncPacket(bytes: ByteArray): RaopSyncPacket {
        require(bytes.size == SYNC_PACKET_BYTES) { "RAOP sync packet must be $SYNC_PACKET_BYTES bytes" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val b0 = buf.get().toInt() and 0xFF
        val b1 = buf.get().toInt() and 0xFF
        require((b0 and 0xE0) == 0x80 && (b1 and 0x7F) == RTP_PAYLOAD_TYPE_TIMING_SYNC) {
            "invalid RAOP sync RTP header"
        }
        buf.short // seq
        val curTs = buf.int.toLong() and 0xFFFF_FFFFL
        val ntpSec = buf.int.toLong() and 0xFFFF_FFFFL
        val ntpFrac = buf.int.toLong() and 0xFFFF_FFFFL
        val nextTs = buf.int.toLong() and 0xFFFF_FFFFL
        return RaopSyncPacket((b0 and 0x10) != 0, curTs, ntpSec, ntpFrac, nextTs)
    }

    /** Linear dB volume mapping (`0.0 .. 1.0` -> AirPlay `-144.0` mute or `-30.0 .. 0.0` dB). */
    fun sliderToRaopDecibels(sliderZeroToOne: Double): Double {
        val clamped = sliderZeroToOne.coerceIn(0.0, 1.0)
        return if (clamped <= 0.001) -144.0 else -30.0 * (1.0 - clamped)
    }

    fun buildPhotoPutHeaders(
        sessionId: String,
        assetKey: String,
        transition: SlideshowTransition = SlideshowTransition.DISSOLVE,
        jpegLengthBytes: Int,
    ): Map<String, String> {
        require(sessionId.isNotBlank() && assetKey.isNotBlank() && jpegLengthBytes > 0) { "invalid photo PUT args" }
        return linkedMapOf(
            "X-Apple-Session-ID" to sessionId,
            "X-Apple-AssetKey" to assetKey,
            "X-Apple-Transition" to transition.wireKey,
            "Content-Type" to "image/jpeg",
            "Content-Length" to jpegLengthBytes.toString(),
        )
    }
}
