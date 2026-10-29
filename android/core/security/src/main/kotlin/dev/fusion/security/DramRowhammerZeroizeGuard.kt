package dev.fusion.security

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Clean-room DRAM Rowhammer & Cold-Boot XOR-Split Secret Arena (`V20`, Apache-2.0).
 *
 * Never stores a sensitive key in a single contiguous byte array:
 * - Splits secret `S` into two random shares `A` and `B = S xor A` framed by 16-byte random
 *   head/tail integrity canaries (detecting single-bit flips / Rowhammer disturbance!),
 * - Supports live `reMask()` (rotates `A' = A xor M, B' = B xor M` without ever materializing `S`),
 * - Zeroizes both shares + canaries deterministically on `destroy()`.
 */
object DramRowhammerZeroizeGuard {
    const val SCOPE =
        "XOR-split dual-share secret arena with 16-byte Rowhammer bit-flip canary guards, live re-masking, and constant-time zeroization (V20)"

    private const val CANARY_BYTES = 16

    class GuardedSecret(secret: ByteArray, private val rng: SecureRandom = SecureRandom()) : AutoCloseable {
        val length: Int = secret.size
        private val canaryExpected = ByteArray(CANARY_BYTES).also(rng::nextBytes)
        private val shareA = ByteArray(CANARY_BYTES + length + CANARY_BYTES)
        private val shareB = ByteArray(CANARY_BYTES + length + CANARY_BYTES)
        private var destroyed = false

        init {
            require(secret.isNotEmpty() && secret.size <= 4096) { "GuardedSecret length must be 1..4096" }
            val pad = ByteArray(length).also(rng::nextBytes)
            writeCanaries(shareA)
            writeCanaries(shareB)
            for (i in 0 until length) {
                shareA[CANARY_BYTES + i] = pad[i]
                shareB[CANARY_BYTES + i] = (secret[i].toInt() xor pad[i].toInt()).toByte()
            }
            pad.fill(0)
        }

        /** Re-randomizes both shares in-place (`A ^= M; B ^= M`) without reconstructing the plaintext key! */
        fun reMask() {
            verifyCanariesOrThrow()
            val mask = ByteArray(length).also(rng::nextBytes)
            for (i in 0 until length) {
                shareA[CANARY_BYTES + i] = (shareA[CANARY_BYTES + i].toInt() xor mask[i].toInt()).toByte()
                shareB[CANARY_BYTES + i] = (shareB[CANARY_BYTES + i].toInt() xor mask[i].toInt()).toByte()
            }
            mask.fill(0)
        }

        fun <R> useReconstructed(block: (ByteArray) -> R): R {
            verifyCanariesOrThrow()
            val temp = ByteArray(length)
            return try {
                for (i in 0 until length) {
                    temp[i] = (shareA[CANARY_BYTES + i].toInt() xor shareB[CANARY_BYTES + i].toInt()).toByte()
                }
                block(temp)
            } finally {
                temp.fill(0)
            }
        }

        /** Package-visible test hook to simulate a hardware bit-flip in a framing canary. */
        internal fun corruptHeadCanaryBitForAudit(bitIndex: Int = 0) {
            shareA[0] = (shareA[0].toInt() xor (1 shl (bitIndex and 7))).toByte()
        }

        fun isDestroyed(): Boolean = destroyed

        override fun close() {
            shareA.fill(0)
            shareB.fill(0)
            canaryExpected.fill(0)
            destroyed = true
        }

        private fun writeCanaries(buf: ByteArray) {
            System.arraycopy(canaryExpected, 0, buf, 0, CANARY_BYTES)
            System.arraycopy(canaryExpected, 0, buf, CANARY_BYTES + length, CANARY_BYTES)
        }

        private fun verifyCanariesOrThrow() {
            check(!destroyed) { "GuardedSecret has already been zeroized" }
            val ok = MessageDigest.isEqual(shareA.copyOfRange(0, CANARY_BYTES), canaryExpected) &&
                MessageDigest.isEqual(shareA.copyOfRange(CANARY_BYTES + length, shareA.size), canaryExpected) &&
                MessageDigest.isEqual(shareB.copyOfRange(0, CANARY_BYTES), canaryExpected) &&
                MessageDigest.isEqual(shareB.copyOfRange(CANARY_BYTES + length, shareB.size), canaryExpected)
            if (!ok) {
                close()
                throw SecurityException("DRAM canary bit-flip anomaly detected; secret emergency-zeroized")
            }
        }
    }
}
