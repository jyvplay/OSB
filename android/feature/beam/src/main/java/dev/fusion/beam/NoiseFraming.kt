// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room Noise Protocol Framework transport-phase AEAD framing & anti-replay engine (`NoiseFraming`).
 *
 * Implements Noise specification §5 (`CipherState`) + §9 (`Noise_*_25519_AESGCM_SHA256` transport framing):
 * - 2-byte big-endian length prefix (`max 65535` bytes per Noise transport message including 16-byte AEAD tag).
 * - 96-bit (`12`-byte) AEAD nonce = 32-bit zero prefix `0x00000000` || 64-bit big-endian monotonic counter `n`
 *   (Noise §4.1 `AESGCM` nonce convention; `n == 2^64 - 1` reserved solely for `rekey()`!).
 * - Sliding 64-packet anti-replay window (`ReplayWindow`) for unordered datagram / LAN P2P channels.
 * - Explicit Noise §4.2 `rekey()` deriving `k' = ENCRYPT(k, maxNonce=0xFFFFFFFFFFFFFFFF, zerolen, zeros_32)[0..31]`.
 */
object NoiseFraming {
    const val SCOPE =
        "Noise Protocol transport CipherState (AES-256-GCM 96-bit 0^32||be64(n) nonce, 2-byte BE length frame, Noise §4.2 rekey(), 64-slot anti-replay window); X25519 XX handshake state machine out of scope."

    const val TAG_BITS = 128
    const val TAG_BYTES = 16
    const val MAX_NOISE_PACKET_BYTES = 65_535
    private const val REKEY_NONCE = -1L // 0xFFFF_FFFF_FFFF_FFFFuL

    class CipherState(initialKey32: ByteArray) {
        private var keyBytes: ByteArray = initialKey32.copyOf()
        var nonce: Long = 0L
            private set

        init {
            require(initialKey32.size == 32) { "Noise AESGCM requires a 32-byte key" }
        }

        fun writeTransportFrame(plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
            require(nonce != REKEY_NONCE) { "Noise nonce exhausted; must rekey" }
            require(plaintext.size + 8 + TAG_BYTES <= MAX_NOISE_PACKET_BYTES) {
                "Plaintext (${plaintext.size}) exceeds 65535-byte Noise frame limit"
            }
            val currentN = nonce++
            val ct = encryptWithNonce(keyBytes, currentN, associatedData, plaintext)
            val payloadLen = 8 + ct.size // 8-byte explicit sequence + AEAD ciphertext+tag
            val frame = ByteArray(2 + payloadLen)
            val bb = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN)
            bb.putShort(payloadLen.toShort())
            bb.putLong(currentN)
            bb.put(ct)
            return frame
        }

        fun readTransportFrame(
            frame: ByteArray,
            replayWindow: ReplayWindow? = null,
            associatedData: ByteArray = ByteArray(0),
        ): ByteArray {
            require(frame.size >= 2 + 8 + TAG_BYTES) { "Truncated Noise transport frame" }
            val bb = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN)
            val declaredLen = bb.short.toInt() and 0xFFFF
            require(declaredLen == frame.size - 2) { "Noise frame length prefix ($declaredLen) != ${frame.size - 2}" }
            val seq = bb.long
            require(seq >= 0L && seq != REKEY_NONCE) { "Invalid Noise packet sequence" }
            if (replayWindow != null && !replayWindow.checkBeforeDecrypt(seq)) {
                throw SecurityException("Rejected replayed or stale Noise transport sequence: $seq")
            }
            val ct = frame.copyOfRange(10, frame.size)
            val pt = decryptWithNonce(keyBytes, seq, associatedData, ct)
            replayWindow?.commitVerified(seq)
            return pt
        }

        /**
         * Noise Protocol §4.2 `REKEY(k)`: encrypts 32 zero bytes at `nonce = 2^64 - 1` with empty AD,
         * taking the first 32 bytes as the new symmetric key and zeroizing the prior key in place!
         */
        fun rekey() {
            val zeros32 = ByteArray(32)
            val block = encryptWithNonce(keyBytes, REKEY_NONCE, ByteArray(0), zeros32)
            keyBytes.fill(0)
            keyBytes = block.copyOfRange(0, 32)
            nonce = 0L
        }

        fun destroy() {
            keyBytes.fill(0)
        }
    }

    class ReplayWindow {
        private var highestSeq: Long = -1L
        private var bitmap: Long = 0L

        fun checkBeforeDecrypt(seq: Long): Boolean {
            if (seq < 0L) return false
            if (highestSeq < 0L) return true
            if (seq > highestSeq) return true
            val diff = highestSeq - seq
            if (diff >= 64L) return false
            return ((bitmap ushr diff.toInt()) and 1L) == 0L
        }

        fun commitVerified(seq: Long) {
            if (highestSeq < 0L) {
                highestSeq = seq
                bitmap = 1L
                return
            }
            if (seq > highestSeq) {
                val shift = seq - highestSeq
                bitmap = if (shift >= 64L) 1L else (bitmap shl shift.toInt()) or 1L
                highestSeq = seq
            } else {
                val diff = (highestSeq - seq).toInt()
                bitmap = bitmap or (1L shl diff)
            }
        }
    }

    private fun nonceBytes(n: Long): ByteArray {
        val iv = ByteArray(12)
        ByteBuffer.wrap(iv, 4, 8).order(ByteOrder.BIG_ENDIAN).putLong(n)
        return iv
    }

    private fun encryptWithNonce(key: ByteArray, n: Long, ad: ByteArray, pt: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonceBytes(n)))
        if (ad.isNotEmpty()) cipher.updateAAD(ad)
        return cipher.doFinal(pt)
    }

    private fun decryptWithNonce(key: ByteArray, n: Long, ad: ByteArray, ct: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonceBytes(n)))
        if (ad.isNotEmpty()) cipher.updateAAD(ad)
        return cipher.doFinal(ct)
    }
}
