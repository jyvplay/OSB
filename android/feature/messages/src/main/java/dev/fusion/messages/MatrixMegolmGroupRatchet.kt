// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room Matrix Megolm 4-Component (`R_{i,0..3}`) Forward-Secure Group Ratchet Engine (`MS-01`).
 *
 * Implements the permissive Matrix Megolm specification's 128-byte (4 x 32-byte) hierarchical
 * hash ratchet:
 * - Every `2^18` steps advances `R0` and reseeds `R1, R2, R3`
 * - Every `2^12` steps advances `R1` and reseeds `R2, R3`
 * - Every `2^6` steps advances `R2` and reseeds `R3`
 * - Every step advances `R3` (`O(1)` forward ratcheting, strictly irreversible backward!)
 */
object MatrixMegolmGroupRatchet {
    const val SCOPE =
        "Pure-JVM Matrix Megolm 4-component hierarchical HMAC-SHA256 group ratchet + AES-256-CTR/HMAC-SHA256 authenticated room frame sealer (MS-01)."

    data class RatchetState(
        val sessionId: String,
        val messageIndex: Int,
        val r0: ByteArray,
        val r1: ByteArray,
        val r2: ByteArray,
        val r3: ByteArray,
    )

    data class SealedRoomEvent(
        val sessionId: String,
        val messageIndex: Int,
        val ciphertext: ByteArray,
        val macTag16: ByteArray,
    )

    fun createOutboundSession(sessionId: String, seed128: ByteArray): RatchetState {
        require(sessionId.isNotBlank() && seed128.size == 128) { "Megolm requires 128-byte (4x32B) seed" }
        return RatchetState(
            sessionId = sessionId,
            messageIndex = 0,
            r0 = seed128.copyOfRange(0, 32),
            r1 = seed128.copyOfRange(32, 64),
            r2 = seed128.copyOfRange(64, 96),
            r3 = seed128.copyOfRange(96, 128),
        )
    }

    fun advanceOneStep(state: RatchetState): RatchetState {
        val nextIdx = state.messageIndex + 1
        return when {
            (nextIdx and 0x0003_FFFF) == 0 -> {
                val nr0 = prf(state.r0, 0x00)
                RatchetState(state.sessionId, nextIdx, nr0, prf(state.r0, 0x01), prf(state.r0, 0x02), prf(state.r0, 0x03))
            }
            (nextIdx and 0x0000_0FFF) == 0 -> {
                val nr1 = prf(state.r1, 0x01)
                RatchetState(state.sessionId, nextIdx, state.r0, nr1, prf(state.r1, 0x02), prf(state.r1, 0x03))
            }
            (nextIdx and 0x0000_003F) == 0 -> {
                val nr2 = prf(state.r2, 0x02)
                RatchetState(state.sessionId, nextIdx, state.r0, state.r1, nr2, prf(state.r2, 0x03))
            }
            else -> RatchetState(state.sessionId, nextIdx, state.r0, state.r1, state.r2, prf(state.r3, 0x03))
        }
    }

    fun advanceToIndex(state: RatchetState, targetIndex: Int): RatchetState {
        require(targetIndex >= state.messageIndex) {
            "Megolm ratchet cannot rewind backward from ${state.messageIndex} to $targetIndex"
        }
        var cur = state
        while (cur.messageIndex < targetIndex) {
            cur = advanceOneStep(cur)
        }
        return cur
    }

    fun encryptEvent(state: RatchetState, plaintextUtf8: String): Pair<SealedRoomEvent, RatchetState> {
        val (aesKey, macKey, iv) = deriveMessageKeys(state)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        val ct = cipher.doFinal(plaintextUtf8.toByteArray(StandardCharsets.UTF_8))
        val mac = computeEventMac(macKey, state.sessionId, state.messageIndex, ct)
        return SealedRoomEvent(state.sessionId, state.messageIndex, ct, mac) to advanceOneStep(state)
    }

    fun decryptEvent(inboundState: RatchetState, event: SealedRoomEvent): Pair<String, RatchetState> {
        require(inboundState.sessionId == event.sessionId) { "sessionId mismatch" }
        val aligned = advanceToIndex(inboundState, event.messageIndex)
        val (aesKey, macKey, iv) = deriveMessageKeys(aligned)
        val expectedMac = computeEventMac(macKey, event.sessionId, event.messageIndex, event.ciphertext)
        require(java.security.MessageDigest.isEqual(expectedMac, event.macTag16)) {
            "Megolm HMAC authentication failed at index ${event.messageIndex}"
        }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        val pt = String(cipher.doFinal(event.ciphertext), StandardCharsets.UTF_8)
        return pt to aligned
    }

    private fun prf(key32: ByteArray, domainByte: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key32, "HmacSHA256"))
        return mac.doFinal(byteArrayOf(domainByte.toByte()))
    }

    private fun deriveMessageKeys(s: RatchetState): Triple<ByteArray, ByteArray, ByteArray> {
        val concat = s.r0 + s.r1 + s.r2 + s.r3
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ByteArray(32) { 0x4D }, "HmacSHA256"))
        val prk = mac.doFinal(concat)
        val aes = prf(prk, 0x11)
        val mKey = prf(prk, 0x22)
        val iv = prf(prk, 0x33).copyOfRange(0, 16)
        return Triple(aes, mKey, iv)
    }

    private fun computeEventMac(macKey: ByteArray, sid: String, idx: Int, ct: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        mac.update(sid.toByteArray(StandardCharsets.UTF_8))
        mac.update(ByteBuffer.allocate(4).putInt(idx).array())
        mac.update(ct)
        return mac.doFinal().copyOfRange(0, 16)
    }
}
