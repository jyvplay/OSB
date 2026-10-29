// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Clean-room IETF **`RFC 3550` RTP 12-Byte Audio Packet Header Codec & RFC 3550 §A.8 Interarrival
 * Jitter / Wraparound Sequence Loss Tracker**.
 */
object RtpAudioPacketHeader {
    const val SCOPE =
        "Clean-room IETF RFC 3550 RTP v2 header codec (12B + CSRC list) and 16-bit sequence-wraparound loss & RFC 3550 §A.8 jitter estimator."

    data class Packet(
        val marker: Boolean,
        val payloadType: Int,
        val sequenceNumber: Int, // 0..65535
        val timestamp: Long,     // 0..2^32-1
        val ssrc: Long,
        val csrcs: List<Long> = emptyList(),
        val payload: ByteArray,
    ) {
        init {
            require(payloadType in 0..127) { "RTP payloadType $payloadType outside 0..127" }
            require(sequenceNumber in 0..65_535) { "RTP sequenceNumber outside 0..65535" }
            require(csrcs.size <= 15) { "CSRC count exceeds 15" }
        }
    }

    fun encode(pkt: Packet): ByteArray {
        val headerLen = 12 + pkt.csrcs.size * 4
        val buf = ByteBuffer.allocate(headerLen + pkt.payload.size).order(ByteOrder.BIG_ENDIAN)
        val b0 = (2 shl 6) or (pkt.csrcs.size and 0x0F) // V=2, P=0, X=0, CC
        val b1 = (if (pkt.marker) 0x80 else 0x00) or (pkt.payloadType and 0x7F)
        buf.put(b0.toByte())
        buf.put(b1.toByte())
        buf.putShort(pkt.sequenceNumber.toShort())
        buf.putInt(pkt.timestamp.toInt())
        buf.putInt(pkt.ssrc.toInt())
        for (c in pkt.csrcs) buf.putInt(c.toInt())
        buf.put(pkt.payload)
        return buf.array()
    }

    fun decode(bytes: ByteArray): Packet {
        require(bytes.size >= 12) { "RTP packet shorter than 12-byte fixed header" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val b0 = buf.get().toInt() and 0xFF
        val version = (b0 ushr 6) and 0x03
        require(version == 2) { "unsupported RTP version $version (expected 2)" }
        val cc = b0 and 0x0F
        val b1 = buf.get().toInt() and 0xFF
        val marker = (b1 and 0x80) != 0
        val pt = b1 and 0x7F
        val seq = buf.short.toInt() and 0xFFFF
        val ts = buf.int.toLong() and 0xFFFF_FFFFL
        val ssrc = buf.int.toLong() and 0xFFFF_FFFFL
        require(12 + cc * 4 <= bytes.size) { "truncated RTP CSRC list" }
        val csrcs = List(cc) { buf.int.toLong() and 0xFFFF_FFFFL }
        val payload = ByteArray(buf.remaining())
        buf.get(payload)
        return Packet(marker, pt, seq, ts, ssrc, csrcs, payload)
    }

    class JitterTracker {
        private var initialized = false
        private var highestExtSeq = 0L
        private var cycles = 0L
        private var received = 0L
        private var baseSeq = 0L
        private var prevTransit = 0L
        var jitterTicks: Double = 0.0
            private set

        fun onPacket(seq16: Int, rtpTimestamp: Long, arrivalRtpClockTicks: Long) {
            if (!initialized) {
                initialized = true
                baseSeq = seq16.toLong()
                highestExtSeq = seq16.toLong()
                prevTransit = arrivalRtpClockTicks - rtpTimestamp
                received = 1L
                return
            }
            val uDelta = (seq16 - (highestExtSeq and 0xFFFFL).toInt()) and 0xFFFF
            if (uDelta < 0x8000) {
                if (seq16 < (highestExtSeq and 0xFFFFL)) cycles += 65_536L
                val ext = cycles + seq16
                if (ext > highestExtSeq) highestExtSeq = ext
            }
            received++
            val transit = arrivalRtpClockTicks - rtpTimestamp
            val d = abs(transit - prevTransit).toDouble()
            prevTransit = transit
            jitterTicks += (d - jitterTicks) / 16.0 // RFC 3550 §A.8 EWMA
        }

        fun lostPackets(): Long {
            if (!initialized) return 0L
            val expected = highestExtSeq - baseSeq + 1L
            return (expected - received).coerceAtLeast(0L)
        }
    }
}
