package dev.fusion.continuity

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room End-to-End Encrypted Cross-Device Continuity Clipboard & File-Drop Envelope
 * (`TD-04`, Apache-2.0).
 *
 * Enforces:
 * - Sensitive credential exclusion (`isSensitiveSecret = true` -> REFUSED immediately so vault
 *   passwords NEVER leave the local device clipboard!),
 * - Monotonic replay-window sequence numbers (`seq > highWaterSeq`),
 * - `60s` (`ttlMs = 60_000L`) hard auto-expiration,
 * - `ChaCha20-Poly1305` authenticated encryption with MIME + expiry bound in `AAD`!
 */
object CrossDeviceClipboardSyncEngine {
    const val SCOPE =
        "E2EE ChaCha20-Poly1305 LAN continuity clipboard & file-drop envelope with sensitive-secret refusal, monotonic anti-replay & 60s TTL (TD-04)"

    private val ALLOWED_MIMES = setOf("text/plain", "text/markdown", "image/png", "application/pdf")

    data class SyncPacket(
        val senderDeviceId: String,
        val sequenceNumber: Long,
        val issuedAtMs: Long,
        val expiresAtMs: Long,
        val mimeType: String,
        val nonce12: ByteArray,
        val ciphertext: ByteArray,
    )

    class PeerChannel(private val sharedKey32: ByteArray) {
        private var outboundSeq = 0L
        private val peerHighWater = HashMap<String, Long>()

        init {
            require(sharedKey32.size == 32) { "sharedKey32 must be 32 bytes" }
        }

        fun sealClipboard(
            senderDeviceId: String,
            mimeType: String,
            payload: ByteArray,
            nowMs: Long,
            isSensitiveSecret: Boolean = false,
            ttlMs: Long = 60_000L,
        ): SyncPacket {
            require(!isSensitiveSecret) {
                "Refusing cross-device sync of EXTRA_IS_SENSITIVE credential clipboard"
            }
            require(mimeType.lowercase() in ALLOWED_MIMES) { "Disallowed continuity MIME '$mimeType'" }
            require(payload.isNotEmpty() && payload.size <= 4 * 1024 * 1024 && ttlMs in 1_000L..300_000L)

            val seq = ++outboundSeq
            val exp = nowMs + ttlMs
            val nonce12 = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).putInt(senderDeviceId.hashCode()).putLong(seq).array()
            val aad = "$senderDeviceId|$seq|$nowMs|$exp|$mimeType".toByteArray(StandardCharsets.UTF_8)
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedKey32, "ChaCha20"), IvParameterSpec(nonce12))
            cipher.updateAAD(aad)
            return SyncPacket(senderDeviceId, seq, nowMs, exp, mimeType, nonce12, cipher.doFinal(payload))
        }

        fun openClipboard(packet: SyncPacket, nowMs: Long): ByteArray {
            require(nowMs <= packet.expiresAtMs && nowMs >= packet.issuedAtMs - 5_000L) {
                "Expired continuity clipboard packet (now=$nowMs exp=${packet.expiresAtMs})"
            }
            val prev = peerHighWater[packet.senderDeviceId] ?: 0L
            require(packet.sequenceNumber > prev) {
                "Replayed continuity clipboard packet seq=${packet.sequenceNumber} <= $prev"
            }
            val aad = "${packet.senderDeviceId}|${packet.sequenceNumber}|${packet.issuedAtMs}|${packet.expiresAtMs}|${packet.mimeType}"
                .toByteArray(StandardCharsets.UTF_8)
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedKey32, "ChaCha20"), IvParameterSpec(packet.nonce12))
            cipher.updateAAD(aad)
            val pt = cipher.doFinal(packet.ciphertext)
            peerHighWater[packet.senderDeviceId] = packet.sequenceNumber
            return pt
        }
    }
}
