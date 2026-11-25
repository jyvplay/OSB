// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

/**
 * Bounded collaborative turn-taking token state machine.
 */
class TogetherTurnToken private constructor() {
    companion object {
        fun create(): TogetherTurnToken = TogetherTurnToken()
    }

    private var holder: String? = null

    fun claim(peerId: String): Boolean {
        require(peerId.isNotEmpty()) { "empty peerId" }
        if (holder != null && holder != peerId) return false
        holder = peerId
        return true
    }

    fun release(peerId: String): Boolean {
        if (holder != peerId) return false
        holder = null
        return true
    }

    fun currentHolder(): String? = holder
}
