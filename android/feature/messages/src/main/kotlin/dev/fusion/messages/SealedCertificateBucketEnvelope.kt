// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * R53 `:feature:messages` power-of-two bucketed sealed-certificate + inner-ciphertext envelope
 * framing codec preventing message-length correlation on untrusted relays.
 */
object SealedCertificateBucketEnvelope {
    const val SCOPE = "Power-of-two bucketed sender-certificate + inner-ciphertext framing; local codec only."

    fun padEnvelope(senderCertificate: ByteArray, innerPayload: ByteArray, bucketBytes: Int = 256): ByteArray {
        require(bucketBytes >= 64 && Integer.bitCount(bucketBytes) == 1) { "bucketBytes must be power of two >= 64" }
        val rawNeed = 9 + senderCertificate.size + innerPayload.size
        var targetLen = bucketBytes
        while (targetLen < rawNeed) targetLen = targetLen shl 1
        require(targetLen <= 1_048_576) { "Envelope exceeds 1 MiB safety bound" }
        val out = ByteArray(targetLen)
        val bb = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(senderCertificate.size)
        bb.put(senderCertificate)
        bb.putInt(innerPayload.size)
        bb.put(innerPayload)
        out[8 + senderCertificate.size + innerPayload.size] = 0x80.toByte()
        return out
    }

    fun unpadEnvelope(padded: ByteArray): Pair<ByteArray, ByteArray> {
        require(padded.size >= 64 && Integer.bitCount(padded.size) == 1) { "Invalid bucketed envelope length" }
        val bb = ByteBuffer.wrap(padded).order(ByteOrder.BIG_ENDIAN)
        val certLen = bb.int
        require(certLen in 0..(padded.size - 9)) { "Corrupt sender certificate length" }
        val cert = ByteArray(certLen)
        bb.get(cert)
        val msgLen = bb.int
        require(msgLen in 0..(padded.size - 9 - certLen)) { "Corrupt inner payload length" }
        val msg = ByteArray(msgLen)
        bb.get(msg)
        val markerIdx = 8 + certLen + msgLen
        require((padded[markerIdx].toInt() and 0xFF) == 0x80) { "Missing 0x80 bucket pad delimiter" }
        for (i in (markerIdx + 1) until padded.size) {
            require(padded[i].toInt() == 0) { "Non-zero padding byte at $i" }
        }
        return cert to msg
    }
}
