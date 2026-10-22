// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

/**
 * 2-of-2 XOR secret splitting for escrow-shaped storage.
 *
 * Sits next to [TotpCode] (code math): nothing splits a vault secret into
 * two shares that are individually useless and jointly sufficient — one
 * share stays on-device, the other goes to the consented backup. Splitting
 * XORs the secret with a caller-supplied mask; combining XORs the shares
 * back. Randomness is caller duty: the mask MUST be uniform-random and
 * used once, which this kernel cannot verify and therefore documents
 * loudly instead of pretending a hash or counter is a mask. Either share
 * alone reveals nothing only under that assumption. Lengths 1 byte to
 * 64 KiB; mismatched share lengths are refused rather than truncated.
 *
 * ByteArray in, ByteArray out; no Android dependency, JVM unit-testable.
 */
object XorSplit {
    const val MAX_LEN = 65536

    /** Splits [secret] under [mask] into (maskShare, blindedShare). */
    fun split(secret: ByteArray, mask: ByteArray): Pair<ByteArray, ByteArray> {
        require(secret.size in 1..MAX_LEN) { "bad secret length" }
        require(mask.size == secret.size) { "mask length mismatch" }
        val blinded = ByteArray(secret.size) { (secret[it].toInt() xor mask[it].toInt()).toByte() }
        return mask.copyOf() to blinded
    }

    /** Recombines two shares from [split]. Refuses mismatched lengths. */
    fun combine(a: ByteArray, b: ByteArray): ByteArray {
        require(a.size in 1..MAX_LEN && b.size in 1..MAX_LEN) { "bad share length" }
        require(a.size == b.size) { "share length mismatch" }
        return ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }
    }
}
