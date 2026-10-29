package dev.fusion.security

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room `age-encryption.org/v1` File Encryption Header & `STREAM` Chunked `ChaCha20-Poly1305`
 * Envelope (Apache-2.0).
 *
 * Implements the C2SP `age` v1 cryptographic design AND explicitly enforces the ETH Zurich
 * (Stauble) audit invariant:
 * - Unwrapped file key MUST equal **strictly `16` bytes** (`FILE_KEY_BYTES = 16`) BEFORE computing
 *   or verifying the header `HMAC-SHA256` MAC (`HKDF-SHA256(fileKey, salt="", info="header")`)!
 * - Payload uses `64 KiB` (`65_536` byte) `STREAM` chunks with 12-byte nonce
 *   `[11-byte big-endian chunk counter || 1-byte lastChunkFlag (0x00 or 0x01)]`, preventing chunk
 *   reorder, splice, and premature-EOF truncation attacks!
 */
object AgeV1StreamCipherEnvelope {
    const val SCOPE =
        "C2SP age v1 header HMAC-SHA256 + strict 16-byte unwrapped fileKey check + 64 KiB STREAM ChaCha20-Poly1305 chunked AEAD"

    const val VERSION_LINE = "age-encryption.org/v1"
    const val FILE_KEY_BYTES = 16
    const val STREAM_CHUNK_PLAINTEXT_BYTES = 65_536
    private val B64_RAW = Base64.getUrlEncoder().withoutPadding()
    private val B64_DEC = Base64.getUrlDecoder()

    fun sealWithSymmetricIdentity(
        identityKey32: ByteArray,
        plaintext: ByteArray,
        rng: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(identityKey32.size == 32) { "identityKey32 must be 32 bytes" }
        val fileKey16 = ByteArray(FILE_KEY_BYTES).also(rng::nextBytes)
        val wrapSalt16 = ByteArray(16).also(rng::nextBytes)
        val streamNonce16 = ByteArray(16).also(rng::nextBytes)

        val wrapKey32 = hkdfSha256(identityKey32, wrapSalt16, "age-encryption.org/v1/sym-wrap".toByteArray(), 32)
        val wrappedFileKey = chacha20Poly1305Seal(wrapKey32, ByteArray(12), fileKey16) // 16 + 16 = 32 bytes

        val headerWithoutMac = buildString {
            append(VERSION_LINE).append('\n')
            append("-> sym-hkdf ").append(B64_RAW.encodeToString(wrapSalt16)).append('\n')
            append(B64_RAW.encodeToString(wrappedFileKey)).append('\n')
            append("---")
        }
        val macKey32 = hkdfSha256(fileKey16, ByteArray(0), "header".toByteArray(StandardCharsets.US_ASCII), 32)
        val headerMac = hmacSha256(macKey32, headerWithoutMac.toByteArray(StandardCharsets.US_ASCII))
        val fullHeader = "$headerWithoutMac ${B64_RAW.encodeToString(headerMac)}\n".toByteArray(StandardCharsets.US_ASCII)

        val payloadKey32 = hkdfSha256(fileKey16, streamNonce16, "payload".toByteArray(StandardCharsets.US_ASCII), 32)
        val out = ByteArrayOutputStream(fullHeader.size + 16 + plaintext.size + 64)
        out.write(fullHeader)
        out.write(streamNonce16)

        if (plaintext.isEmpty()) {
            out.write(chacha20Poly1305Seal(payloadKey32, streamNonce(0L, isFinal = true), ByteArray(0)))
        } else {
            var offset = 0
            var counter = 0L
            while (offset < plaintext.size) {
                val len = minOf(STREAM_CHUNK_PLAINTEXT_BYTES, plaintext.size - offset)
                val isFinal = (offset + len) == plaintext.size
                val slice = plaintext.copyOfRange(offset, offset + len)
                out.write(chacha20Poly1305Seal(payloadKey32, streamNonce(counter, isFinal), slice))
                offset += len
                counter++
            }
        }
        fileKey16.fill(0)
        return out.toByteArray()
    }

    fun openWithSymmetricIdentity(identityKey32: ByteArray, envelopeBytes: ByteArray): ByteArray {
        require(identityKey32.size == 32) { "identityKey32 must be 32 bytes" }
        val rawStr = String(envelopeBytes, 0, minOf(envelopeBytes.size, 8192), StandardCharsets.ISO_8859_1)
        val macLineIdx = rawStr.indexOf("\n--- ")
        require(macLineIdx > 0) { "Malformed age v1 header: missing '--- <MAC>' line" }
        val headerLineEnd = rawStr.indexOf('\n', macLineIdx + 1)
        require(headerLineEnd > macLineIdx) { "Unterminated age v1 header MAC line" }

        val headerLines = rawStr.substring(0, headerLineEnd).split('\n')
        require(headerLines.size >= 4 && headerLines[0] == VERSION_LINE) { "Invalid age version line" }
        require(headerLines[1].startsWith("-> sym-hkdf ")) { "Unsupported age recipient stanza" }

        val wrapSalt16 = B64_DEC.decode(headerLines[1].removePrefix("-> sym-hkdf ").trim())
        val wrappedFileKey = B64_DEC.decode(headerLines[2].trim())
        val expectedMac = B64_DEC.decode(headerLines[3].removePrefix("--- ").trim())

        val wrapKey32 = hkdfSha256(identityKey32, wrapSalt16, "age-encryption.org/v1/sym-wrap".toByteArray(), 32)
        val unwrappedFileKey = chacha20Poly1305Open(wrapKey32, ByteArray(12), wrappedFileKey)

        // CRITICAL ETHZ STAUBLE INVARIANT: enforce strict 16-byte fileKey length BEFORE header MAC!
        require(unwrappedFileKey.size == FILE_KEY_BYTES) {
            "Refusing non-canonical age file key length ${unwrappedFileKey.size} (must be $FILE_KEY_BYTES bytes)"
        }

        val headerPrefixBytes = envelopeBytes.copyOfRange(0, macLineIdx + 4) // includes "\n---"
        val macKey32 = hkdfSha256(unwrappedFileKey, ByteArray(0), "header".toByteArray(StandardCharsets.US_ASCII), 32)
        val actualMac = hmacSha256(macKey32, headerPrefixBytes)
        require(MessageDigest.isEqual(expectedMac, actualMac)) { "age v1 header HMAC verification failed" }

        val bodyStart = headerLineEnd + 1
        require(bodyStart + 16 + 16 <= envelopeBytes.size) { "Truncated age STREAM payload" }
        val streamNonce16 = envelopeBytes.copyOfRange(bodyStart, bodyStart + 16)
        val payloadKey32 = hkdfSha256(unwrappedFileKey, streamNonce16, "payload".toByteArray(StandardCharsets.US_ASCII), 32)
        unwrappedFileKey.fill(0)

        var pos = bodyStart + 16
        var counter = 0L
        var sawFinalChunk = false
        val plainOut = ByteArrayOutputStream()
        val maxCtChunk = STREAM_CHUNK_PLAINTEXT_BYTES + 16

        while (pos < envelopeBytes.size) {
            val rem = envelopeBytes.size - pos
            val ctLen = minOf(maxCtChunk, rem)
            val isLastInStream = (pos + ctLen) == envelopeBytes.size
            val chunkCt = envelopeBytes.copyOfRange(pos, pos + ctLen)
            val pt = chacha20Poly1305Open(payloadKey32, streamNonce(counter, isFinal = isLastInStream), chunkCt)
            if (!isLastInStream && pt.size != STREAM_CHUNK_PLAINTEXT_BYTES) {
                throw SecurityException("Non-final STREAM chunk smaller than 64 KiB")
            }
            if (isLastInStream) {
                if (counter > 0L && pt.isEmpty()) {
                    throw SecurityException("Forbidden empty final STREAM chunk on non-empty payload")
                }
                sawFinalChunk = true
            }
            plainOut.write(pt)
            pos += ctLen
            counter++
        }
        require(sawFinalChunk) { "Truncated age STREAM: missing authenticated final chunk" }
        return plainOut.toByteArray()
    }

    private fun streamNonce(counter: Long, isFinal: Boolean): ByteArray {
        val n = ByteArray(12)
        ByteBuffer.wrap(n, 3, 8).order(ByteOrder.BIG_ENDIAN).putLong(counter)
        n[11] = if (isFinal) 0x01 else 0x00
        return n
    }

    private fun chacha20Poly1305Seal(key32: ByteArray, nonce12: ByteArray, pt: ByteArray): ByteArray {
        val c = Cipher.getInstance("ChaCha20-Poly1305")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key32, "ChaCha20"), IvParameterSpec(nonce12))
        return c.doFinal(pt)
    }

    private fun chacha20Poly1305Open(key32: ByteArray, nonce12: ByteArray, ct: ByteArray): ByteArray {
        val c = Cipher.getInstance("ChaCha20-Poly1305")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key32, "ChaCha20"), IvParameterSpec(nonce12))
        return c.doFinal(ct)
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val effSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prk = hmacSha256(effSalt, ikm)
        val okm = ByteArray(outLen)
        var t = ByteArray(0)
        var pos = 0
        var block: Byte = 1
        while (pos < outLen) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(block)
            t = mac.doFinal()
            val copy = minOf(t.size, outLen - pos)
            System.arraycopy(t, 0, okm, pos, copy)
            pos += copy
            block++
        }
        return okm
    }

    private fun hmacSha256(key: ByteArray, msg: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(msg)
    }
}
