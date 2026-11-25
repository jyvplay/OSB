// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Bounded 64-bit capability bitmask.
 */
class CapabilityMaskBitset private constructor(private var mask: Long) {
    companion object {
        fun empty(): CapabilityMaskBitset = CapabilityMaskBitset(0L)
    }

    fun grant(bitIndex: Int) {
        require(bitIndex in 0..63) { "bitIndex out of bounds" }
        mask = mask or (1L shl bitIndex)
    }

    fun revoke(bitIndex: Int) {
        require(bitIndex in 0..63) { "bitIndex out of bounds" }
        mask = mask and (1L shl bitIndex).inv()
    }

    fun has(bitIndex: Int): Boolean {
        require(bitIndex in 0..63) { "bitIndex out of bounds" }
        return (mask and (1L shl bitIndex)) != 0L
    }
}
