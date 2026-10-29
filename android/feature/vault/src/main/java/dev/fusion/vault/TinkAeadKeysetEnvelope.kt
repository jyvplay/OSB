// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room Tink-compatible Streaming AES-256-GCM-HKDF Keyset Container (`SC-07`).
 *
 * Provides:
 * - Multi-key `KeysetEnvelope` supporting key rotation (`ENABLED`, `DISABLED`) with explicit
 *   `primaryKeyId` 4-byte wire prefix and zero cloud key escrow.
 * - Chunked streaming AEAD (`chunkSize = 4096` default) binding `(containerHeader || chunkIndex || isFinalByte)`
 *   into every chunk's AES-GCM Additional Authenticated Data (AAD), defeating chunk reordering,
 *   splicing, and premature truncation attacks!
 */
object TinkAeadKeysetEnvelope {
    const val MAGIC = "FSTK1"
    const val GCM_NONCE_PREFIX_BYTES = 7
    const val GCM_TAG_BITS = 128

    enum class KeyStatus { ENABLED, DISABLED }

    data class KeyEntry(
        val keyId: Int,
        val status: KeyStatus,
        val rawAes256Key: ByteArray,
    ) {
        init {
            require(rawAes256Key.size == 32) { "AES-256 key must be 32 bytes" }
        }
    }

    class Keyset(
        val primaryKeyId: Int,
        val entries: List<KeyEntry>,
    ) {
        init {
            val primary = entries.firstOrNull { it.keyId == primaryKeyId }
            require(primary != null && primary.status == KeyStatus.ENABLED) {
                "primaryKeyId $primaryKeyId must exist and be ENABLED"
            }
            require(entries.map { it.keyId }.distinct().size == entries.size) { "duplicate keyId in Keyset" }
        }

        fun rotateWithNewPrimary(newKeyId: Int, csprng: SecureRandom = SecureRandom()): Keyset {
            require(entries.none { it.keyId == newKeyId }) { "keyId $newKeyId already exists" }
            val fresh = ByteArray(32).also(csprng::nextBytes)
            return Keyset(newKeyId, entries + KeyEntry(newKeyId, KeyStatus.ENABLED, fresh))
        }
    }

    private val csprng = SecureRandom()

    fun encryptStream(
        keyset: Keyset,
        plaintext: ByteArray,
        associatedContext: ByteArray,
        chunkSize: Int = 4_096,
    ): ByteArray {
        require(chunkSize in 64..65_536) { "chunkSize outside 64..65536" }
        val primary = keyset.entries.first { it.keyId == keyset.primaryKeyId }
        val salt = ByteArray(16).also(csprng::nextBytes)
        val noncePrefix = ByteArray(GCM_NONCE_PREFIX_BYTES).also(csprng::nextBytes)
        val subkey = hkdfSha256(primary.rawAes256Key, salt, associatedContext, 32)

        val chunks = if (plaintext.isEmpty()) {
            listOf(ByteArray(0))
        } else {
            val list = ArrayList<ByteArray>((plaintext.size + chunkSize - 1) / chunkSize)
            var off = 0
            while (off < plaintext.size) {
                val end = minOf(plaintext.size, off + chunkSize)
                list.add(plaintext.copyOfRange(off, end))
                off = end
            }
            list
        }

        // Header: MAGIC(5B) + keyId(4B) + chunkSize(4B) + chunkCount(4B) + salt(16B) + noncePrefix(7B) = 40 bytes
        val header = ByteBuffer.allocate(40).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC.encodeToByteArray())
            putInt(primary.keyId)
            putInt(chunkSize)
            putInt(chunks.size)
            put(salt)
            put(noncePrefix)
        }.array()

        val encryptedChunks = chunks.mapIndexed { idx, plainChunk ->
            val isLast = idx == chunks.lastIndex
            val nonce = chunkNonce(noncePrefix, idx, isLast)
            val aad = chunkAad(header, associatedContext, idx, isLast)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(subkey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(plainChunk)
        }

        val totalSize = header.size + encryptedChunks.sumOf { 4 + it.size }
        val out = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        out.put(header)
        for (enc in encryptedChunks) {
            out.putInt(enc.size)
            out.put(enc)
        }
        return out.array()
    }

    fun decryptStream(
        keyset: Keyset,
        container: ByteArray,
        associatedContext: ByteArray,
    ): ByteArray {
        require(container.size >= 44) { "container truncated (< 44 bytes)" }
        val header = container.copyOfRange(0, 40)
        val hBuf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val magicBytes = ByteArray(5).also(hBuf::get)
        require(magicBytes.decodeToString() == MAGIC) { "invalid container magic" }
        val keyId = hBuf.int
        val chunkSize = hBuf.int
        val chunkCount = hBuf.int
        require(chunkSize in 64..65_536 && chunkCount in 1..65_536) { "invalid container chunk header" }
        val salt = ByteArray(16).also(hBuf::get)
        val noncePrefix = ByteArray(GCM_NONCE_PREFIX_BYTES).also(hBuf::get)

        val keyEntry = keyset.entries.firstOrNull { it.keyId == keyId && it.status == KeyStatus.ENABLED }
            ?: throw SecurityException("keyId $keyId missing or disabled in Keyset")
        val subkey = hkdfSha256(keyEntry.rawAes256Key, salt, associatedContext, 32)

        val bodyBuf = ByteBuffer.wrap(container, 40, container.size - 40).order(ByteOrder.BIG_ENDIAN)
        val outParts = ArrayList<ByteArray>(chunkCount)
        for (idx in 0 until chunkCount) {
            require(bodyBuf.remaining() >= 4) { "truncated chunk length at chunk $idx" }
            val cLen = bodyBuf.int
            require(cLen >= 16 && bodyBuf.remaining() >= cLen) { "truncated ciphertext at chunk $idx" }
            val ct = ByteArray(cLen).also(bodyBuf::get)
            val isLast = idx == chunkCount - 1
            val nonce = chunkNonce(noncePrefix, idx, isLast)
            val aad = chunkAad(header, associatedContext, idx, isLast)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(subkey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(aad)
            val pt = cipher.doFinal(ct)
            if (!isLast) require(pt.size == chunkSize) { "non-final chunk $idx size mismatch" }
            outParts.add(pt)
        }
        require(!bodyBuf.hasRemaining()) { "trailing unauthenticated bytes after final chunk" }
        val totalPt = outParts.sumOf { it.size }
        val joined = ByteArray(totalPt)
        var pos = 0
        for (p in outParts) {
            System.arraycopy(p, 0, joined, pos, p.size)
            pos += p.size
        }
        return joined
    }

    private fun chunkNonce(prefix7: ByteArray, index: Int, isLast: Boolean): ByteArray =
        ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).apply {
            put(prefix7)
            putInt(index)
            put(if (isLast) 1.toByte() else 0.toByte())
        }.array()

    private fun chunkAad(header: ByteArray, context: ByteArray, index: Int, isLast: Boolean): ByteArray =
        ByteBuffer.allocate(header.size + context.size + 5).order(ByteOrder.BIG_ENDIAN).apply {
            put(header)
            put(context)
            putInt(index)
            put(if (isLast) 1.toByte() else 0.toByte())
        }.array()

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(1.toByte())
        return mac.doFinal().copyOf(outLen)
    }
}
