// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room **Symmetric KDF Double-Ratchet Sending/Receiving Chain** with Forward-Secret Per-Message
 * Key Erasure and Bounded Out-of-Order Skipped-Message-Key Dictionary (`MAX_SKIP = 64`).
 */
class DoubleRatchetHeaderChain(initialChainKey32: ByteArray) {
    companion object {
        const val SCOPE =
            "Clean-room HMAC-SHA256 symmetric KDF ratchet + AES-256-GCM AEAD per-message forward secrecy + bounded MAX_SKIP=64 out-of-order key cache."

        const val MAX_SKIP = 64
    }

    init {
        require(initialChainKey32.size == 32) { "initial chain key must be 32 bytes" }
    }

    data class RatchetPacket(
        val msgNumber: Int,
        val iv12: ByteArray,
        val ciphertextWithTag: ByteArray,
    )

    private var sendChainKey = initialChainKey32.copyOf()
    private var recvChainKey = initialChainKey32.copyOf()
    private var sendCount = 0
    private var recvCount = 0
    private val skippedMessageKeys = LinkedHashMap<Int, ByteArray>()

    fun encryptNext(plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): RatchetPacket {
        val (nextCk, msgKey) = stepKdf(sendChainKey)
        sendChainKey.fill(0) // immediate forward-secret erasure of prior chain key!
        sendChainKey = nextCk
        val num = sendCount++
        val iv = deriveIv12(msgKey, num)
        val ct = aesGcmEncrypt(msgKey, iv, plaintext, associatedData)
        msgKey.fill(0)
        return RatchetPacket(num, iv, ct)
    }

    fun decryptPacket(packet: RatchetPacket, associatedData: ByteArray = ByteArray(0)): ByteArray {
        require(packet.msgNumber >= 0) { "negative msgNumber" }
        val skipped = skippedMessageKeys.remove(packet.msgNumber)
        if (skipped != null) {
            val pt = aesGcmDecrypt(skipped, packet.iv12, packet.ciphertextWithTag, associatedData)
            skipped.fill(0)
            return pt
        }
        require(packet.msgNumber >= recvCount) {
            "replay or already-consumed message #${packet.msgNumber} refused (forward-secret key erased)"
        }
        require(packet.msgNumber - recvCount <= MAX_SKIP) {
            "message skip gap (${packet.msgNumber - recvCount}) exceeds MAX_SKIP=$MAX_SKIP"
        }
        while (recvCount < packet.msgNumber) {
            val (nextCk, mk) = stepKdf(recvChainKey)
            recvChainKey.fill(0)
            recvChainKey = nextCk
            skippedMessageKeys[recvCount++] = mk
        }
        val (nextCk, targetMk) = stepKdf(recvChainKey)
        recvChainKey.fill(0)
        recvChainKey = nextCk
        recvCount++
        val pt = aesGcmDecrypt(targetMk, packet.iv12, packet.ciphertextWithTag, associatedData)
        targetMk.fill(0)
        return pt
    }

    private fun stepKdf(ck: ByteArray): Pair<ByteArray, ByteArray> {
        val msgKey = hmac(ck, byteArrayOf(0x01))
        val nextCk = hmac(ck, byteArrayOf(0x02))
        return nextCk to msgKey
    }

    private fun deriveIv12(mk: ByteArray, n: Int): ByteArray =
        hmac(mk, byteArrayOf(0x49, 0x56, ((n ushr 24) and 0xFF).toByte(), ((n ushr 16) and 0xFF).toByte(), ((n ushr 8) and 0xFF).toByte(), (n and 0xFF).toByte())).copyOfRange(0, 12)

    private fun hmac(key: ByteArray, input: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        return m.doFinal(input)
    }

    private fun aesGcmEncrypt(key32: ByteArray, iv12: ByteArray, pt: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key32, "AES"), GCMParameterSpec(128, iv12))
        if (aad.isNotEmpty()) c.updateAAD(aad)
        return c.doFinal(pt)
    }

    private fun aesGcmDecrypt(key32: ByteArray, iv12: ByteArray, ct: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key32, "AES"), GCMParameterSpec(128, iv12))
        if (aad.isNotEmpty()) c.updateAAD(aad)
        return c.doFinal(ct)
    }
}
