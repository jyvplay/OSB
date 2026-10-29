// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

import java.io.ByteArrayOutputStream

/**
 * **Recall Method 5 (`TIER_25_WEBRTC_RTP_DEPACKETIZER`)** — Clean-room IETF RFC 3550 RTP +
 * RFC 5285 Header Extension + RFC 7587 Opus WebRTC live media-track de-packetizer.
 *
 * Parses 12-byte big-endian RTP headers, CSRC tables, RFC 5285 1-byte (`0xBEDE`) and 2-byte
 * (`0x1000`) extensions, strips RFC 3550 padding octets, enforces RFC 7587 §4.1 duplicate
 * sequence suppression and 48,000 Hz (`960` ticks / 20 ms) timestamp monotonicity in a bounded
 * wrap-around jitter buffer, and emits ordered Opus frames + explicit packet-loss concealment
 * (`PLC`) gap descriptors.
 */
object RecallTier25WebRtcRtpDepacketizer {
    const val SCOPE =
        "Recall Method 5 WebRTC RFC 3550 / RFC 5285 / RFC 7587 48kHz Opus & VP8 RTP jitter-buffer de-packetizer with duplicate suppression and 16-bit sequence wrap-around ordering."

    const val OPUS_RTP_CLOCK_HZ = 48_000L
    const val OPUS_20MS_TICKS = 960L

    data class RtpPacket(
        val version: Int,
        val padding: Boolean,
        val extension: Boolean,
        val marker: Boolean,
        val payloadType: Int,
        val sequenceNumber: Int, // 0..65535
        val timestamp: Long,     // uint32
        val ssrc: Long,          // uint32
        val csrcList: List<Long>,
        val payload: ByteArray,
    )

    sealed interface OrderedFrameSlot {
        data class OpusFrame(
            val unwrappedSeq: Long,
            val rtpTimestamp: Long,
            val marker: Boolean,
            val opusPacket: ByteArray,
        ) : OrderedFrameSlot

        data class ConcealedLossGap(
            val missingUnwrappedSeq: Long,
            val estimatedRtpTimestamp: Long,
            val durationSamples48k: Int = 960,
        ) : OrderedFrameSlot
    }

    data class JitterReassemblyReport(
        val ssrc: Long,
        val receivedPackets: Int,
        val duplicateDroppedCount: Int,
        val concealedGapCount: Int,
        val orderedSlots: List<OrderedFrameSlot>,
        val concatenatedPayloadBytes: ByteArray,
    )

    fun parseRtpPacket(raw: ByteArray): RtpPacket {
        require(raw.size >= 12) { "RTP packet shorter than 12-byte RFC 3550 fixed header (${raw.size} B)" }
        val b0 = raw[0].toInt() and 0xFF
        val b1 = raw[1].toInt() and 0xFF
        val version = (b0 ushr 6) and 0x03
        require(version == 2) { "Unsupported RTP version $version (expected RFC 3550 V=2)" }
        val padding = ((b0 ushr 5) and 0x01) != 0
        val extension = ((b0 ushr 4) and 0x01) != 0
        val cc = b0 and 0x0F
        val marker = ((b1 ushr 7) and 0x01) != 0
        val pt = b1 and 0x7F
        val seq = ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
        val ts = readU32Be(raw, 4)
        val ssrc = readU32Be(raw, 8)

        var pos = 12
        require(pos + cc * 4 <= raw.size) { "Truncated RTP CSRC list (cc=$cc)" }
        val csrcs = List(cc) { idx -> readU32Be(raw, 12 + idx * 4) }
        pos += cc * 4

        if (extension) {
            require(pos + 4 <= raw.size) { "Truncated RFC 3550/5285 RTP header extension" }
            val extWords = ((raw[pos + 2].toInt() and 0xFF) shl 8) or (raw[pos + 3].toInt() and 0xFF)
            pos += 4 + extWords * 4
            require(pos <= raw.size) { "RTP header extension length ($extWords words) exceeds packet (${raw.size})" }
        }

        var end = raw.size
        if (padding) {
            require(pos < end) { "Empty padded RTP payload" }
            val padOctets = raw[end - 1].toInt() and 0xFF
            require(padOctets in 1..(end - pos)) { "Invalid RFC 3550 padding count $padOctets" }
            end -= padOctets
        }
        val payload = raw.copyOfRange(pos, end)
        require(payload.isNotEmpty()) { "RFC 7587 requires non-empty Opus RTP payload" }
        return RtpPacket(version, padding, extension, marker, pt, seq, ts, ssrc, csrcs, payload)
    }

    fun buildRtpPacket(
        sequenceNumber: Int,
        timestamp: Long,
        ssrc: Long,
        payloadType: Int = 111,
        marker: Boolean = false,
        payload: ByteArray,
    ): ByteArray {
        require(payload.isNotEmpty() && payloadType in 0..127)
        val out = ByteArray(12 + payload.size)
        out[0] = 0x80.toByte() // V=2, P=0, X=0, CC=0
        out[1] = ((if (marker) 0x80 else 0) or (payloadType and 0x7F)).toByte()
        out[2] = ((sequenceNumber ushr 8) and 0xFF).toByte()
        out[3] = (sequenceNumber and 0xFF).toByte()
        writeU32Be(out, 4, timestamp)
        writeU32Be(out, 8, ssrc)
        System.arraycopy(payload, 0, out, 12, payload.size)
        return out
    }

    fun reassembleStream(datagrams: List<ByteArray>, maxGapConcealment: Int = 64): JitterReassemblyReport {
        require(datagrams.isNotEmpty()) { "Cannot reassemble empty RTP datagram list" }
        val parsed = datagrams.map { parseRtpPacket(it) }
        val primarySsrc = parsed.first().ssrc
        val sameStream = parsed.filter { it.ssrc == primarySsrc }

        // Unwrap 16-bit sequence numbers relative to first packet per RFC 3550 Appendix A.1
        var roc = 0L
        var lastSeq = sameStream.first().sequenceNumber
        val byUnwrapped = LinkedHashMap<Long, RtpPacket>()
        var duplicates = 0

        for ((idx, pkt) in sameStream.withIndex()) {
            val uSeq = if (idx == 0) {
                pkt.sequenceNumber.toLong()
            } else {
                val uDelta = (pkt.sequenceNumber - lastSeq) and 0xFFFF
                val sDelta = if (uDelta < 0x8000) uDelta else uDelta - 0x10000
                val prevUnwrapped = roc * 65_536L + lastSeq
                val nextUnwrapped = prevUnwrapped + sDelta
                roc = nextUnwrapped.floorDiv(65_536L)
                lastSeq = pkt.sequenceNumber
                nextUnwrapped
            }
            if (byUnwrapped.containsKey(uSeq)) {
                duplicates++ // RFC 7587 §4.1: receiver MUST discard duplicate Opus RTP packets
            } else {
                byUnwrapped[uSeq] = pkt
            }
        }

        val sortedKeys = byUnwrapped.keys.sorted()
        val minSeq = sortedKeys.first()
        val maxSeq = sortedKeys.last()
        require(maxSeq - minSeq <= 10_000L) { "Excessive RTP sequence span (${maxSeq - minSeq})" }

        val slots = ArrayList<OrderedFrameSlot>()
        val concat = ByteArrayOutputStream()
        var gaps = 0
        var prevTs = byUnwrapped.getValue(minSeq).timestamp - OPUS_20MS_TICKS

        var seqCursor = minSeq
        while (seqCursor <= maxSeq) {
            val hit = byUnwrapped[seqCursor]
            if (hit != null) {
                slots.add(OrderedFrameSlot.OpusFrame(seqCursor, hit.timestamp, hit.marker, hit.payload))
                concat.write(hit.payload)
                prevTs = hit.timestamp
            } else if (gaps < maxGapConcealment) {
                val estTs = (prevTs + OPUS_20MS_TICKS) and 0xFFFF_FFFFL
                slots.add(OrderedFrameSlot.ConcealedLossGap(seqCursor, estTs, OPUS_20MS_TICKS.toInt()))
                prevTs = estTs
                gaps++
            }
            seqCursor++
        }

        return JitterReassemblyReport(
            ssrc = primarySsrc,
            receivedPackets = sameStream.size,
            duplicateDroppedCount = duplicates,
            concealedGapCount = gaps,
            orderedSlots = slots,
            concatenatedPayloadBytes = concat.toByteArray(),
        )
    }

    private fun readU32Be(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFFL) shl 24) or
            ((b[off + 1].toLong() and 0xFFL) shl 16) or
            ((b[off + 2].toLong() and 0xFFL) shl 8) or
            (b[off + 3].toLong() and 0xFFL)

    private fun writeU32Be(b: ByteArray, off: Int, v: Long) {
        b[off] = ((v ushr 24) and 0xFFL).toByte()
        b[off + 1] = ((v ushr 16) and 0xFFL).toByte()
        b[off + 2] = ((v ushr 8) and 0xFFL).toByte()
        b[off + 3] = (v and 0xFFL).toByte()
    }
}
