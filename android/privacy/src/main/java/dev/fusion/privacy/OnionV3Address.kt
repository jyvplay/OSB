// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Clean-room Tor Rendezvous Specification v3 **`.onion` Address Codec & Validator** (`rend-spec-v3.txt §6`)
 * powered by a from-scratch **NIST FIPS 202 `SHA3-256` (`Keccak-f[1600]`)** sponge!
 *
 * Note: Java 17 provides `MessageDigest.getInstance("SHA3-256")`, AND we implement `sha3_256`
 * directly from the FIPS 202 `Keccak-f[1600]` permutation (`r=1088, c=512, domain=0x06`) so it
 * executes deterministically on every JVM / Android API level without JCA provider differences!
 *
 * Tor v3 address formula (`56` lowercase RFC 4648 base32 chars + `".onion"`):
 *   `CHECKSUM = SHA3-256(".onion checksum" || PUBKEY(32) || VERSION(0x03))[0..1]`
 *   `onion_address = base32(PUBKEY(32) || CHECKSUM(2) || 0x03) + ".onion"` (`35` bytes -> `56` chars)
 */
object OnionV3Address {
    const val SCOPE =
        "Clean-room Tor Rendezvous Spec v3 56-char .onion address encoder/validator with from-scratch FIPS 202 Keccak-f[1600] SHA3-256 checksum; legacy broken 16-char v2 RSA .onion addresses strictly refused."

    const val VERSION_V3: Byte = 0x03
    private val CHECKSUM_PREFIX = ".onion checksum".toByteArray(Charsets.US_ASCII)
    private const val BASE32_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"

    data class DecodedOnionV3(
        val ed25519PublicKey: ByteArray,
        val checksum: ByteArray,
        val version: Int,
    )

    fun fromEd25519PublicKey(pubKey32: ByteArray): String {
        require(pubKey32.size == 32) { "Ed25519 public key must be 32 bytes, got ${pubKey32.size}" }
        val csum = computeChecksum(pubKey32)
        val raw35 = ByteArray(35)
        System.arraycopy(pubKey32, 0, raw35, 0, 32)
        raw35[32] = csum[0]
        raw35[33] = csum[1]
        raw35[34] = VERSION_V3
        return encodeBase32Lower(raw35) + ".onion"
    }

    fun validateAndDecode(hostOrAddress: String): DecodedOnionV3 {
        val clean = hostOrAddress.trim().lowercase().removeSuffix(".")
        require(clean.endsWith(".onion")) { "expected .onion suffix: '$hostOrAddress'" }
        val label = clean.removeSuffix(".onion").substringAfterLast('.')
        require(label.length != 16) { "deprecated Tor v2 16-char RSA .onion address refused" }
        require(label.length == 56) { "Tor v3 .onion label must be 56 base32 chars, got ${label.length}" }
        val raw35 = decodeBase32Lower(label)
        require(raw35.size == 35) { "decoded .onion payload must be 35 bytes" }
        val version = raw35[34].toInt() and 0xFF
        require(version == 0x03) { "unsupported .onion version $version (expected v3 = 3)" }
        val pubKey = raw35.copyOfRange(0, 32)
        val expected = computeChecksum(pubKey)
        require(raw35[32] == expected[0] && raw35[33] == expected[1]) {
            "Tor v3 .onion SHA3-256 checksum mismatch"
        }
        return DecodedOnionV3(pubKey, byteArrayOf(raw35[32], raw35[33]), version)
    }

    fun isValidV3(hostOrAddress: String): Boolean =
        runCatching { validateAndDecode(hostOrAddress) }.isSuccess

    private fun computeChecksum(pubKey32: ByteArray): ByteArray {
        val msg = ByteArray(CHECKSUM_PREFIX.size + 32 + 1)
        System.arraycopy(CHECKSUM_PREFIX, 0, msg, 0, CHECKSUM_PREFIX.size)
        System.arraycopy(pubKey32, 0, msg, CHECKSUM_PREFIX.size, 32)
        msg[msg.lastIndex] = VERSION_V3
        val digest = sha3_256(msg)
        return byteArrayOf(digest[0], digest[1])
    }

    /** Clean-room NIST FIPS 202 `SHA3-256` (`r = 136` bytes, `c = 64` bytes, domain suffix `0x06`). */
    fun sha3_256(input: ByteArray): ByteArray {
        val rateBytes = 136
        val state = LongArray(25)
        var offset = 0
        while (offset + rateBytes <= input.size) {
            xorBlock(state, input, offset, rateBytes)
            keccakF1600(state)
            offset += rateBytes
        }
        val block = ByteArray(rateBytes)
        val rem = input.size - offset
        if (rem > 0) System.arraycopy(input, offset, block, 0, rem)
        block[rem] = 0x06
        block[rateBytes - 1] = (block[rateBytes - 1].toInt() or 0x80).toByte()
        xorBlock(state, block, 0, rateBytes)
        keccakF1600(state)

        val out = ByteArray(32)
        for (lane in 0 until 4) {
            val v = state[lane]
            for (b in 0 until 8) {
                out[lane * 8 + b] = ((v ushr (b * 8)) and 0xFFL).toByte()
            }
        }
        return out
    }

    private fun xorBlock(state: LongArray, buf: ByteArray, offset: Int, rateBytes: Int) {
        for (lane in 0 until (rateBytes / 8)) {
            var v = 0L
            for (b in 0 until 8) {
                v = v or ((buf[offset + lane * 8 + b].toLong() and 0xFFL) shl (b * 8))
            }
            state[lane] = state[lane] xor v
        }
    }

    private val RC = longArrayOf(
        0x0000000000000001UL.toLong(), 0x0000000000008082UL.toLong(),
        0x800000000000808AUL.toLong(), 0x8000000080008000UL.toLong(),
        0x000000000000808BUL.toLong(), 0x0000000080000001UL.toLong(),
        0x8000000080008081UL.toLong(), 0x8000000000008009UL.toLong(),
        0x000000000000008AUL.toLong(), 0x0000000000000088UL.toLong(),
        0x0000000080008009UL.toLong(), 0x000000008000000AUL.toLong(),
        0x000000008000808BUL.toLong(), 0x800000000000008BUL.toLong(),
        0x8000000000008089UL.toLong(), 0x8000000000008003UL.toLong(),
        0x8000000000008002UL.toLong(), 0x8000000000000080UL.toLong(),
        0x000000000000800AUL.toLong(), 0x800000008000000AUL.toLong(),
        0x8000000080008081UL.toLong(), 0x8000000000008080UL.toLong(),
        0x0000000080000001UL.toLong(), 0x8000000080008008UL.toLong(),
    )

    private val RHO = intArrayOf(
        0, 1, 62, 28, 27, 36, 44, 6, 55, 20, 3, 10, 43, 25, 39, 41, 45, 15, 21, 8, 18, 2, 61, 56, 14,
    )

    private fun keccakF1600(a: LongArray) {
        val c = LongArray(5)
        val d = LongArray(5)
        val b = LongArray(25)
        for (round in 0 until 24) {
            for (x in 0 until 5) {
                c[x] = a[x] xor a[x + 5] xor a[x + 10] xor a[x + 15] xor a[x + 20]
            }
            for (x in 0 until 5) {
                d[x] = c[(x + 4) % 5] xor java.lang.Long.rotateLeft(c[(x + 1) % 5], 1)
                for (y in 0 until 25 step 5) a[x + y] = a[x + y] xor d[x]
            }
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    val idx = x + 5 * y
                    val newX = y
                    val newY = (2 * x + 3 * y) % 5
                    b[newX + 5 * newY] = java.lang.Long.rotateLeft(a[idx], RHO[idx])
                }
            }
            for (y in 0 until 25 step 5) {
                for (x in 0 until 5) {
                    a[x + y] = b[x + y] xor ((b[(x + 1) % 5 + y].inv()) and b[(x + 2) % 5 + y])
                }
            }
            a[0] = a[0] xor RC[round]
        }
    }

    private fun encodeBase32Lower(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(BASE32_ALPHABET[(buffer ushr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString()
    }

    private fun decodeBase32Lower(s: String): ByteArray {
        val out = ByteArray(s.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        var idx = 0
        for (ch in s) {
            val v = when (ch) {
                in 'a'..'z' -> ch - 'a'
                in '2'..'7' -> 26 + (ch - '2')
                else -> throw IllegalArgumentException("invalid RFC 4648 base32 char '$ch'")
            }
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out[idx++] = ((buffer ushr bitsLeft) and 0xFF).toByte()
            }
        }
        return out
    }
}
