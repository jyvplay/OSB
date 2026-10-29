// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room WinZip AE-1 / AE-2 (`0x9901` extra field, compression method `99`) encrypted ZIP
 * header codec, PBKDF2-HMAC-SHA1 (1,000-iteration per WinZip AE spec) key-material deriver,
 * 2-byte password-verification value checker, and 10-byte HMAC-SHA1-80 authenticator (`FM-05`).
 */
object ZipAesExtraFieldCodec {
    const val HEADER_ID_AES = 0x9901
    const val ZIP_METHOD_WINZIP_AES = 99
    const val PBKDF2_ITERATIONS = 1_000
    const val AUTH_CODE_BYTES = 10

    enum class AesStrength(val modeByte: Int, val saltBytes: Int, val keyBytes: Int) {
        AES_128(1, 8, 16),
        AES_192(2, 12, 24),
        AES_256(3, 16, 32);

        companion object {
            fun fromMode(mode: Int): AesStrength = entries.firstOrNull { it.modeByte == mode }
                ?: throw IllegalArgumentException("unsupported WinZip AES strength mode $mode")
        }
    }

    data class AesExtraHeader(
        val vendorVersion: Int, // 1 = AE-1, 2 = AE-2
        val vendorId: String,   // "AE"
        val strength: AesStrength,
        val actualCompressionMethod: Int, // 0 = STORE, 8 = DEFLATE
    ) {
        init {
            require(vendorVersion in 1..2) { "WinZip AES vendorVersion must be 1 (AE-1) or 2 (AE-2)" }
            require(vendorId == "AE") { "WinZip AES vendorId must be 'AE'" }
            require(actualCompressionMethod in setOf(0, 8)) { "unsupported inner compression $actualCompressionMethod" }
        }
    }

    class DerivedZipAesKeys(
        val aesKey: ByteArray,
        val hmacKey: ByteArray,
        val passwordVerifier2B: ByteArray,
    )

    fun encodeExtraField(header: AesExtraHeader): ByteArray {
        val buf = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(HEADER_ID_AES.toShort())
        buf.putShort(7) // data size
        buf.putShort(header.vendorVersion.toShort())
        buf.put('A'.code.toByte())
        buf.put('E'.code.toByte())
        buf.put(header.strength.modeByte.toByte())
        buf.putShort(header.actualCompressionMethod.toShort())
        return buf.array()
    }

    fun decodeExtraField(extra: ByteArray): AesExtraHeader {
        var pos = 0
        while (pos + 4 <= extra.size) {
            val buf = ByteBuffer.wrap(extra, pos, extra.size - pos).order(ByteOrder.LITTLE_ENDIAN)
            val id = buf.short.toInt() and 0xFFFF
            val sz = buf.short.toInt() and 0xFFFF
            require(pos + 4 + sz <= extra.size) { "truncated ZIP extra field 0x${id.toString(16)}" }
            if (id == HEADER_ID_AES) {
                require(sz == 7) { "WinZip 0x9901 extra payload must be 7 bytes, got $sz" }
                val ver = buf.short.toInt() and 0xFFFF
                val v0 = buf.get().toInt().toChar()
                val v1 = buf.get().toInt().toChar()
                val mode = buf.get().toInt() and 0xFF
                val comp = buf.short.toInt() and 0xFFFF
                return AesExtraHeader(ver, "$v0$v1", AesStrength.fromMode(mode), comp)
            }
            pos += 4 + sz
        }
        throw IllegalArgumentException("0x9901 WinZip AES extra field not present")
    }

    fun deriveKeys(password: CharArray, salt: ByteArray, strength: AesStrength): DerivedZipAesKeys {
        require(password.isNotEmpty()) { "empty ZIP AES password refused" }
        require(salt.size == strength.saltBytes) { "expected ${strength.saltBytes}-byte salt, got ${salt.size}" }
        val totalBits = (2 * strength.keyBytes + 2) * 8
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, totalBits)
        val raw = try {
            skf.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        val kLen = strength.keyBytes
        return DerivedZipAesKeys(
            aesKey = raw.copyOfRange(0, kLen),
            hmacKey = raw.copyOfRange(kLen, 2 * kLen),
            passwordVerifier2B = raw.copyOfRange(2 * kLen, 2 * kLen + 2),
        )
    }

    fun computeHmacSha1Trunc80(hmacKey: ByteArray, ciphertext: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA1"))
        return mac.doFinal(ciphertext).copyOf(AUTH_CODE_BYTES)
    }

    fun constantTimeVerify(expected: ByteArray, actual: ByteArray): Boolean {
        if (expected.size != actual.size) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (expected[i].toInt() xor actual[i].toInt())
        return diff == 0
    }
}
