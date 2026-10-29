// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room Cast Receiver Constant-Time 6-Digit On-Screen PIN Session Binder & RTP Sequence
 * Reorder Buffer.
 */
object CastSinkSrpPinVerifier {
    const val SCOPE =
        "Pure-JVM constant-time HMAC-SHA256 on-screen 6-digit PIN pairing verifier with 3-strike lockout and 16-bit RTP wrap-safe packet reorder buffer."

    fun computePairingProof(pin6Digits: String, clientNonce: ByteArray, sinkNonce: ByteArray): ByteArray {
        require(pin6Digits.matches(Regex("^\\d{6}$")) && clientNonce.size >= 16 && sinkNonce.size >= 16) {
            "expected 6-digit PIN and >=16B nonces"
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pin6Digits.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
        mac.update(clientNonce)
        mac.update(sinkNonce)
        return mac.doFinal()
    }

    fun verifyPairingConstantTime(
        expectedPin6: String,
        clientNonce: ByteArray,
        sinkNonce: ByteArray,
        candidateProof: ByteArray,
        priorFailedAttempts: Int,
    ): Pair<Boolean, Int> {
        if (priorFailedAttempts >= 3) return false to priorFailedAttempts
        val expected = computePairingProof(expectedPin6, clientNonce, sinkNonce)
        val ok = MessageDigest.isEqual(expected, candidateProof)
        return if (ok) true to 0 else false to (priorFailedAttempts + 1)
    }

    /**
     * Orders 16-bit unsigned RTP sequence numbers across `65535 -> 0` wraparound (`RFC 3550`).
     */
    fun sortRtpSequenceWindow(seqNumbers: List<Int>): List<Int> {
        require(seqNumbers.isNotEmpty() && seqNumbers.all { it in 0..65535 }) { "invalid 16-bit RTP sequence numbers" }
        val anchor = seqNumbers.first()
        return seqNumbers.distinct().sortedBy { seq ->
            ((seq - anchor + 32768) and 0xFFFF) - 32768
        }
    }
}
