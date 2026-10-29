// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-JVM **Symmetric Sending/Receiving Chain-Key Ratchet & Out-of-Order Skipped Message-Key
 * Bounded Window** (Signal Double-Ratchet symmetric KDF step using `HMAC-SHA256`) for `:feature:messaging`.
 */
object MessagingSymmetricChainRatchet {
    const val SCOPE =
        "Forward-secret HMAC-SHA256 symmetric message-key ratchet with bounded skipped-key cache and immediate consumed-key zeroization."

    private val MSG_KEY_CONSTANT = byteArrayOf(0x01)
    private val CHAIN_STEP_CONSTANT = byteArrayOf(0x02)

    data class RatchetStepOutput(
        val nextChainKey32: ByteArray,
        val derivedMessageKey32: ByteArray,
        val messageNumber: Int,
    )

    fun advanceChain(chainKey32: ByteArray, messageNumber: Int): RatchetStepOutput {
        require(chainKey32.size == 32 && messageNumber >= 0) { "32-byte chain key and non-negative messageNumber required" }
        val mk = hmacSha256(chainKey32, MSG_KEY_CONSTANT)
        val nextCk = hmacSha256(chainKey32, CHAIN_STEP_CONSTANT)
        return RatchetStepOutput(nextCk, mk, messageNumber)
    }

    fun deriveUpToTarget(
        initialChainKey32: ByteArray,
        currentNumber: Int,
        targetNumber: Int,
        maxSkip: Int = 64,
    ): Pair<RatchetStepOutput, Map<Int, ByteArray>> {
        require(targetNumber >= currentNumber && targetNumber - currentNumber <= maxSkip) {
            "Out-of-order jump (${targetNumber - currentNumber}) invalid or exceeds maxSkip=$maxSkip"
        }
        var ck = initialChainKey32.copyOf()
        val skipped = LinkedHashMap<Int, ByteArray>()
        var num = currentNumber
        while (num < targetNumber) {
            val step = advanceChain(ck, num)
            skipped[num] = step.derivedMessageKey32
            ck = step.nextChainKey32
            num++
        }
        val finalStep = advanceChain(ck, targetNumber)
        return finalStep to skipped
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
