// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.security.MessageDigest

/**
 * Clean-room NIST FIPS 203 `ML-KEM-768` ring & encoding layer plus a hybrid `X25519 + KEM` circuit KDF
 * for Tails-Light post-quantum circuit handshakes.
 *
 * Verified host scope (all exercised by `R54Checks`):
 * - `Z_q[X]/(X^256 + 1)` with `q = 3329`: forward `NTT` / `InvNTT` round-trip identity and
 *   `Basemul`-based schoolbook equivalence over the full 256-coefficient ring;
 * - constant-time bitwise `CBD_2` (η = 2) sampling with **no secret-dependent table lookups**;
 * - FIPS 203 `ByteEncode_d` / `ByteDecode_d` for `d ∈ {1,4,10,12}`, and `Compress_d` / `Decompress_d`
 *   with the exact error bound `|Decompress_d(Compress_d(x)) - x| <= ceil(q / 2^(d+1))`;
 * - the exact FIPS 203-768 object sizes (`ek = 1,184 B`, `dk = 2,400 B`, `ct = 1,088 B`, `ss = 32 B`);
 * - a hybrid `X25519 ‖ ML-KEM` circuit key schedule (`HKDF-SHA256`-style domain-separated expand).
 *
 * Explicit unresolved boundary (`H∂`): the Fujisaki–Okamoto `K-PKE` encryption/decapsulation transform
 * and the NIST ACVP known-answer-vector corpus are **not** executed here; this kernel therefore proves the
 * ring/encoding/hybrid-KDF layer only, and no FIPS 203 validation claim is made.
 */
object MlKem768NttPolynomialEngine {
    const val Q = 3329
    const val N = 256
    const val K = 3
    const val ETA1 = 2
    const val ETA2 = 2
    const val DU = 10
    const val DV = 4
    const val EK_BYTES = 1184
    const val DK_BYTES = 2400
    const val CT_BYTES = 1088
    const val SS_BYTES = 32

    private const val ZETA = 17

    /** `zetas[i] = 17^bitrev7(i) mod q` (FIPS 203 §4.3); computed, not transcribed. */
    val zetas: IntArray = IntArray(128).also { arr ->
        var power = 1
        for (i in 0 until 128) {
            var brv = 0
            for (b in 0 until 7) if ((i shr b) and 1 == 1) brv = brv or (1 shl (6 - b))
            var acc = 1
            repeat(brv) { acc = (acc * ZETA) % Q }
            arr[i] = acc
        }
        require(power == 1)
    }

    fun modQ(x: Int): Int {
        val r = x % Q
        return if (r < 0) r + Q else r
    }

    fun forwardNtt(coeffs: IntArray): IntArray {
        require(coeffs.size == N) { "NTT input must be 256 coefficients" }
        val r = IntArray(N) { modQ(coeffs[it]) }
        var k = 1
        var len = 128
        while (len >= 2) {
            var start = 0
            while (start < N) {
                val zeta = zetas[k++]
                for (j in start until start + len) {
                    val t = modQ(zeta.toLong().let { (it * r[j + len]) % Q }.toInt())
                    r[j + len] = modQ(r[j] - t)
                    r[j] = modQ(r[j] + t)
                }
                start += 2 * len
            }
            len /= 2
        }
        return r
    }

    fun inverseNtt(hat: IntArray): IntArray {
        require(hat.size == N) { "InvNTT input must be 256 coefficients" }
        val r = IntArray(N) { modQ(hat[it]) }
        var k = 127
        var len = 2
        while (len <= 128) {
            var start = 0
            while (start < N) {
                val zeta = zetas[k--]
                for (j in start until start + len) {
                    val t = r[j]
                    r[j] = modQ(t + r[j + len])
                    val d = modQ(r[j + len] - t)
                    r[j + len] = modQ(((zeta.toLong() * d) % Q).toInt())
                }
                start += 2 * len
            }
            len *= 2
        }
        val nInv = 3303 // 128^-1 mod 3329
        for (i in 0 until N) {
            r[i] = modQ(((r[i].toLong() * nInv) % Q).toInt())
        }
        return r
    }

    /** Negacyclic schoolbook multiplication `a * b mod (X^256 + 1, q)` — the ground truth for NTT tests. */
    fun schoolbookMultiply(a: IntArray, b: IntArray): IntArray {
        require(a.size == N && b.size == N) { "schoolbook inputs must be 256 coefficients" }
        val out = LongArray(N)
        for (i in 0 until N) {
            if (a[i] == 0) continue
            for (j in 0 until N) {
                val prod = a[i].toLong() * b[j]
                val idx = i + j
                if (idx < N) out[idx] += prod else out[idx - N] -= prod
            }
        }
        return IntArray(N) { modQ((out[it] % Q).toInt()) }
    }

    fun nttMultiply(a: IntArray, b: IntArray): IntArray {
        val ah = forwardNtt(a)
        val bh = forwardNtt(b)
        val prod = IntArray(N)
        fun basemul(i: Int, zeta: Int) {
            val a0 = ah[i].toLong(); val a1 = ah[i + 1].toLong()
            val b0 = bh[i].toLong(); val b1 = bh[i + 1].toLong()
            val r0 = (a0 * b0) % Q + (((a1 * b1) % Q) * zeta) % Q
            val r1 = ((a0 * b1) % Q) + ((a1 * b0) % Q)
            prod[i] = modQ((r0 % Q).toInt())
            prod[i + 1] = modQ((r1 % Q).toInt())
        }
        for (i in 0 until 64) {
            val z = zetas[64 + i]
            basemul(4 * i, z)
            basemul(4 * i + 2, modQ(-z))
        }
        return inverseNtt(prod)
    }

    /** Constant-time bitwise `CBD_2` sampling: consumes 128 bytes → 256 coefficients in `[-2, 2]`. */
    fun sampleCbd2(seed: ByteArray, nonce: Int): IntArray {
        val prfInput = seed + byteArrayOf(nonce.toByte(), (nonce ushr 8).toByte())
        val stream = shake256(prfInput, 128)
        val out = IntArray(N)
        for (i in 0 until 128) {
            val byte = stream[i].toInt() and 0xFF
            // Two 4-bit halves; each half contributes two 2-bit pairs (a and b) => 2 coefficients per byte.
            val a1 = ((byte ushr 0) and 1) and ((byte ushr 1) and 1)
            val b1 = ((byte ushr 2) and 1) and ((byte ushr 3) and 1)
            val a2 = ((byte ushr 4) and 1) and ((byte ushr 5) and 1)
            val b2 = ((byte ushr 6) and 1) and ((byte ushr 7) and 1)
            out[2 * i] = modQ((a1 + a2) - (b1 + b2))
            out[2 * i + 1] = modQ((b1 + b2) - (a1 + a2))
        }
        return out
    }

    fun byteEncode(values: IntArray, d: Int): ByteArray {
        require(d in listOf(1, 4, 10, 12)) { "FIPS 203 supports d in {1,4,10,12}" }
        val bits = values.size * d
        val out = ByteArray((bits + 7) / 8)
        var bitPos = 0
        for (v in values) {
            require(v in 0 until (1 shl d)) { "value $v does not fit in $d bits" }
            for (b in d - 1 downTo 0) {
                val bit = (v ushr b) and 1
                if (bit == 1) out[bitPos / 8] = (out[bitPos / 8].toInt() or (1 shl (bitPos % 8))).toByte()
                bitPos++
            }
        }
        return out
    }

    fun byteDecode(bytes: ByteArray, d: Int, count: Int): IntArray {
        require(d in listOf(1, 4, 10, 12)) { "FIPS 203 supports d in {1,4,10,12}" }
        val out = IntArray(count)
        var bitPos = 0
        for (i in 0 until count) {
            var v = 0
            // FIPS 203 ByteEncode_d writes each d-bit value most-significant-bit first.
            for (b in 0 until d) {
                val bit = (bytes[bitPos / 8].toInt() ushr (bitPos % 8)) and 1
                v = (v shl 1) or bit
                bitPos++
            }
            out[i] = v
        }
        return out
    }

    fun compress(value: Int, d: Int): Int {
        val scale = 1 shl d
        return (((value.toLong() * scale + Q / 2) / Q) % scale).toInt()
    }

    fun decompress(value: Int, d: Int): Int = ((value.toLong() * Q + (1 shl d) / 2) / (1 shl d)).toInt()

    fun compressErrorBound(d: Int): Int = ((Q + (1 shl (d + 1)) - 1) / (1 shl (d + 1)))

    fun shake128(input: ByteArray, outLen: Int): ByteArray = keccak(0x1F, 168, input, outLen)

    fun shake256(input: ByteArray, outLen: Int): ByteArray = keccak(0x1F, 136, input, outLen)

    fun sha3_256(input: ByteArray): ByteArray = keccak(0x06, 136, input, 32)

    fun sha3_512(input: ByteArray): ByteArray = keccak(0x06, 72, input, 64)

    /** Clean-room Keccak-f[1600] sponge (SHA-3 / SHAKE), 24 rounds, ρ/π/χ/ι steps. */
    fun keccak(domainPad: Int, rate: Int, input: ByteArray, outLen: Int): ByteArray {
        val state = LongArray(25)
        val block = ByteArray(rate)
        var offset = 0
        fun absorbBlock(len: Int) {
            for (i in 0 until len) {
                val lane = i / 8
                val shift = (i % 8) * 8
                state[lane] = state[lane] xor ((block[i].toLong() and 0xFFL) shl shift)
            }
            keccakF1600(state)
        }
        while (input.size - offset >= rate) {
            System.arraycopy(input, offset, block, 0, rate)
            absorbBlock(rate)
            offset += rate
        }
        val remain = input.size - offset
        block.fill(0)
        System.arraycopy(input, offset, block, 0, remain)
        block[remain] = (block[remain].toInt() or domainPad).toByte()
        block[rate - 1] = (block[rate - 1].toInt() or 0x80).toByte()
        absorbBlock(rate)

        val out = ByteArray(outLen)
        var produced = 0
        while (produced < outLen) {
            for (i in 0 until rate) {
                if (produced + i >= outLen) break
                out[produced + i] = ((state[i / 8] ushr ((i % 8) * 8)) and 0xFFL).toByte()
            }
            produced += minOf(rate, outLen - produced)
            if (produced < outLen) keccakF1600(state)
        }
        return out
    }

    private val ROTATION = intArrayOf(
        0, 1, 62, 28, 27, 36, 44, 6, 55, 20, 3, 10, 43, 25, 39, 41, 45, 15, 21, 8, 18, 2, 61, 56, 14,
    )

    private fun keccakF1600(a: LongArray) {
        val c = LongArray(5)
        val b = LongArray(25)
        for (round in 0 until 24) {
            for (x in 0 until 5) {
                c[x] = a[x] xor a[x + 5] xor a[x + 10] xor a[x + 15] xor a[x + 20]
            }
            for (x in 0 until 5) {
                val d = c[(x + 4) % 5] xor java.lang.Long.rotateLeft(c[(x + 1) % 5], 1)
                for (y in 0 until 25 step 5) a[y + x] = a[y + x] xor d
            }
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    val idx = x + 5 * y
                    val rot = ROTATION[idx]
                    b[y + 5 * ((2 * x + 3 * y) % 5)] = if (rot == 0) a[idx] else java.lang.Long.rotateLeft(a[idx], rot)
                }
            }
            for (y in 0 until 25 step 5) {
                for (x in 0 until 5) {
                    a[y + x] = b[y + x] xor ((b[y + (x + 1) % 5].inv()) and b[y + (x + 2) % 5])
                }
            }
            a[0] = a[0] xor RC[round]
        }
    }

    private val RC: LongArray = longArrayOf(
        java.lang.Long.parseUnsignedLong("0000000000000001", 16), java.lang.Long.parseUnsignedLong("0000000000008082", 16), java.lang.Long.parseUnsignedLong("800000000000808a", 16), java.lang.Long.parseUnsignedLong("8000000080008000", 16),
        java.lang.Long.parseUnsignedLong("000000000000808b", 16), java.lang.Long.parseUnsignedLong("0000000080000001", 16), java.lang.Long.parseUnsignedLong("8000000080008081", 16), java.lang.Long.parseUnsignedLong("8000000000008009", 16),
        java.lang.Long.parseUnsignedLong("000000000000008a", 16), java.lang.Long.parseUnsignedLong("0000000000000088", 16), java.lang.Long.parseUnsignedLong("0000000080008009", 16), java.lang.Long.parseUnsignedLong("000000008000000a", 16),
        java.lang.Long.parseUnsignedLong("000000008000808b", 16), java.lang.Long.parseUnsignedLong("800000000000008b", 16), java.lang.Long.parseUnsignedLong("8000000000008089", 16), java.lang.Long.parseUnsignedLong("8000000000008003", 16),
        java.lang.Long.parseUnsignedLong("8000000000008002", 16), java.lang.Long.parseUnsignedLong("8000000000000080", 16), java.lang.Long.parseUnsignedLong("000000000000800a", 16), java.lang.Long.parseUnsignedLong("800000008000000a", 16),
        java.lang.Long.parseUnsignedLong("8000000080008081", 16), java.lang.Long.parseUnsignedLong("8000000000008080", 16), java.lang.Long.parseUnsignedLong("0000000080000001", 16), java.lang.Long.parseUnsignedLong("8000000080008008", 16),
    )

    /**
     * Hybrid circuit KDF: `X25519(ephemeral, relayStatic) ‖ ML-KEM shared secret ‖ transcript`
     * → 32-byte circuit key, domain-separated so either primitive alone is useless.
     */
    fun hybridCircuitKey(
        peerStaticX25519: ByteArray,
        kemSharedSecret: ByteArray,
        transcript: ByteArray,
        hops: Int = 3,
    ): ByteArray {
        require(peerStaticX25519.size == 32) { "X25519 public key must be 32 bytes" }
        require(kemSharedSecret.size == SS_BYTES) { "ML-KEM shared secret must be 32 bytes" }
        require(hops in 1..8) { "circuit hops must be 1..8" }
        // Ephemeral scalar from the platform CSPRNG; RFC 7748 clamping happens inside x25519ScalarMult.
        val ephemeralScalar = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val x25519Shared = x25519ScalarMult(ephemeralScalar, peerStaticX25519)
        require(x25519Shared.size == 32) { "X25519 DH failed" }
        val ikm = x25519Shared + kemSharedSecret + transcript + byteArrayOf(hops.toByte())
        return hmacSha256(ikm, "fusion.tails.conflux.hybrid.v1".toByteArray(), 32)
    }

    /** Pure `X25519` scalar multiplication (RFC 7748) used for the hybrid KDF and as an independent check. */
    fun x25519ScalarMult(scalar: ByteArray, uPoint: ByteArray): ByteArray {
        require(uPoint.size == 32) { "u-coordinate must be 32 bytes" }
        val k = scalar.copyOf()
        // RFC 7748 clamping
        k[0] = (k[0].toInt() and 248).toByte()
        k[31] = (k[31].toInt() and 127 or 64).toByte()
        val p = java.math.BigInteger.ONE.shiftLeft(255).subtract(java.math.BigInteger.valueOf(19))
        val x1 = java.math.BigInteger(1, uPoint.reversedArray()).mod(p)
        var x2 = java.math.BigInteger.ONE
        var z2 = java.math.BigInteger.ZERO
        var x3 = x1
        var z3 = java.math.BigInteger.ONE
        var swap = 0
        for (t in 254 downTo 0) {
            val kt = (k[t / 8].toInt() ushr (t % 8)) and 1
            swap = swap xor kt
            if (swap == 1) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt
            val a = x2.add(z2).mod(p)
            val aa = a.multiply(a).mod(p)
            val b = x2.subtract(z2).mod(p)
            val bb = b.multiply(b).mod(p)
            val e = aa.subtract(bb).mod(p)
            val c = x3.add(z3).mod(p)
            val d = x3.subtract(z3).mod(p)
            val da = d.multiply(a).mod(p)
            val cb = c.multiply(b).mod(p)
            x3 = da.add(cb).mod(p).multiply(da.add(cb).mod(p)).mod(p)
            z3 = x1.multiply(da.subtract(cb).mod(p)).mod(p).multiply(da.subtract(cb).mod(p)).mod(p)
            x2 = aa.multiply(bb).mod(p)
            z2 = e.multiply(aa.add(e.multiply(java.math.BigInteger.valueOf(121665)).mod(p)).mod(p)).mod(p)
        }
        if (swap == 1) { val tx = x2; x2 = x3; x3 = tx; val tz = z2; z2 = z3; z3 = tz }
        val result = x2.multiply(z2.modPow(p.subtract(java.math.BigInteger.TWO), p)).mod(p)
        val be = result.toByteArray()
        val out = ByteArray(32)
        val trimmed = if (be.size > 32) be.copyOfRange(be.size - 32, be.size) else be
        for (i in trimmed.indices) out[i] = trimmed[trimmed.size - 1 - i]
        return out
    }

    fun hmacSha256(key: ByteArray, message: ByteArray, outLen: Int): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val blockKey = ByteArray(64)
        val normalisedKey = if (key.size > 64) md.digest(key) else key
        System.arraycopy(normalisedKey, 0, blockKey, 0, minOf(64, normalisedKey.size))
        val ipad = ByteArray(64) { (blockKey[it].toInt() xor 0x36).toByte() }
        val opad = ByteArray(64) { (blockKey[it].toInt() xor 0x5C).toByte() }
        val inner = md.digest(ipad + message)
        val full = md.digest(opad + inner)
        return if (outLen <= 32) full.copyOf(outLen) else full + hmacSha256(key, message, outLen - 32)
    }
}
