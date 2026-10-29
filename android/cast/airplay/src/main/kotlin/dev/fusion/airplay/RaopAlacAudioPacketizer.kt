package dev.fusion.airplay

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room RAOP / AirPlay Lossless Audio RTP Packetizer (`AP-05`, Apache-2.0).
 *
 * Packetizes 16-bit 44.1 kHz stereo PCM frames (`352` samples/frame = `1408` PCM bytes) into
 * verbatim ALAC (`tag=1` uncompressed 16-bit escape frame, zero proprietary encoder tables!)
 * inside RFC 3550 RTP (`PT=96` audio, `PT=84` NTP/RTP sync, `PT=86` retransmit slice) sealed
 * with standard JCA `ChaCha20-Poly1305` AEAD.
 */
object RaopAlacAudioPacketizer {
    const val SCOPE =
        "Clean-room RAOP/AirPlay RTP ALAC lossless 352-sample audio packetizer + ChaCha20-Poly1305 payload AEAD + PT=84 sync & PT=86 retransmit (AP-05)"

    const val SAMPLES_PER_FRAME = 352
    const val CHANNELS = 2
    const val PCM_BYTES_PER_FRAME = SAMPLES_PER_FRAME * CHANNELS * 2 // 1408 bytes
    const val RTP_PT_AUDIO = 96
    const val RTP_PT_SYNC = 84
    const val RTP_PT_RETRANSMIT = 86

    data class RaopPacket(
        val sequenceNumber: Int,
        val rtpTimestamp: Long,
        val ssrc: Int,
        val wireBytes: ByteArray,
    )

    class Session(
        private val sessionKey32: ByteArray,
        val ssrc: Int = 0x52414F50,
        initialSeq: Int = 1,
        initialTimestamp: Long = 0L,
    ) {
        private var seq: Int = initialSeq and 0xFFFF
        private var rtpTs: Long = initialTimestamp and 0xFFFF_FFFFL
        private val ringBuffer = LinkedHashMap<Int, RaopPacket>()

        init {
            require(sessionKey32.size == 32) { "ChaCha20-Poly1305 sessionKey must be 32 bytes" }
        }

        fun packetizePcmFrame(pcmLittleEndian16Stereo: ByteArray, markerFirstPacket: Boolean = false): RaopPacket {
            require(pcmLittleEndian16Stereo.size == PCM_BYTES_PER_FRAME) {
                "RAOP ALAC frame requires $PCM_BYTES_PER_FRAME bytes (352 stereo 16-bit samples), got ${pcmLittleEndian16Stereo.size}"
            }
            val curSeq = seq
            val curTs = rtpTs
            seq = (seq + 1) and 0xFFFF
            rtpTs = (rtpTs + SAMPLES_PER_FRAME) and 0xFFFF_FFFFL

            // 12-byte RTP header: V=2 (0x80), M|PT=96 (0x60 or 0xE0), seq (u16 BE), timestamp (u32 BE), ssrc (u32 BE)
            val rtpHdr = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).apply {
                put(0x80.toByte())
                put((if (markerFirstPacket) (0x80 or RTP_PT_AUDIO) else RTP_PT_AUDIO).toByte())
                putShort(curSeq.toShort())
                putInt(curTs.toInt())
                putInt(ssrc)
            }.array()

            // Clean-room ALAC verbatim uncompressed stereo block header (3 bytes: element=CPE(1), escape=1, sampleSize=16)
            val alacFrame = ByteArray(3 + pcmLittleEndian16Stereo.size)
            alacFrame[0] = 0x20 // tag=1 (CPE), 0000
            alacFrame[1] = 0x02 // uncommitted=0, hasSize=0, uncompressed=1
            alacFrame[2] = 0x10 // 16-bit verbatim samples follow
            System.arraycopy(pcmLittleEndian16Stereo, 0, alacFrame, 3, pcmLittleEndian16Stereo.size)

            val nonce12 = buildNonce(curSeq, curTs)
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionKey32, "ChaCha20"), IvParameterSpec(nonce12))
            cipher.updateAAD(rtpHdr)
            val sealed = cipher.doFinal(alacFrame)

            val wire = ByteArray(rtpHdr.size + 8 + sealed.size) // rtpHdr + 8-byte nonce tail + ciphertext+tag
            System.arraycopy(rtpHdr, 0, wire, 0, 12)
            System.arraycopy(nonce12, 4, wire, 12, 8)
            System.arraycopy(sealed, 0, wire, 20, sealed.size)

            val pkt = RaopPacket(curSeq, curTs, ssrc, wire)
            ringBuffer[curSeq] = pkt
            if (ringBuffer.size > 256) ringBuffer.remove(ringBuffer.keys.first())
            return pkt
        }

        fun openPacketToPcm(wireBytes: ByteArray): ByteArray {
            require(wireBytes.size >= 12 + 8 + 3 + 16) { "Truncated RAOP RTP packet" }
            val rtpHdr = wireBytes.copyOfRange(0, 12)
            val nonce12 = ByteArray(12)
            System.arraycopy(wireBytes, 12, nonce12, 4, 8)
            val sealed = wireBytes.copyOfRange(20, wireBytes.size)
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionKey32, "ChaCha20"), IvParameterSpec(nonce12))
            cipher.updateAAD(rtpHdr)
            val alac = cipher.doFinal(sealed)
            require(alac.size == 3 + PCM_BYTES_PER_FRAME && alac[0] == 0x20.toByte() && alac[1] == 0x02.toByte()) {
                "Invalid ALAC verbatim frame header"
            }
            return alac.copyOfRange(3, alac.size)
        }

        fun handleRetransmitRange(firstSeq: Int, count: Int): List<RaopPacket> =
            (0 until count.coerceIn(0, 64)).mapNotNull { ringBuffer[(firstSeq + it) and 0xFFFF] }

        fun buildSyncPacket(ntpSeconds: Long, ntpFraction: Long): ByteArray =
            ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN).apply {
                put(0x90.toByte())
                put((0x80 or RTP_PT_SYNC).toByte())
                putShort(7)
                putInt(((rtpTs - SAMPLES_PER_FRAME) and 0xFFFF_FFFFL).toInt())
                putInt(ntpSeconds.toInt())
                putInt(ntpFraction.toInt())
                putInt(rtpTs.toInt())
            }.array()

        private fun buildNonce(seqNum: Int, ts: Long): ByteArray =
            ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0)
                .putInt(seqNum)
                .putInt(ts.toInt())
                .array()
    }
}
