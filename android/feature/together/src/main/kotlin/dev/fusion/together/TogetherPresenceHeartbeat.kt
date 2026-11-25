// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

/**
 * Bounded peer presence heartbeat tracker.
 */
class TogetherPresenceHeartbeat private constructor(private val staleThresholdMs: Long) {
    companion object {
        fun create(staleThresholdMs: Long): TogetherPresenceHeartbeat {
            require(staleThresholdMs > 0L) { "staleThresholdMs must be positive" }
            return TogetherPresenceHeartbeat(staleThresholdMs)
        }
    }

    private val lastSeen = HashMap<String, Long>()

    fun beat(peerId: String, nowMs: Long) {
        require(peerId.isNotEmpty() && nowMs >= 0L) { "invalid beat" }
        lastSeen[peerId] = nowMs
    }

    fun activePeers(nowMs: Long): List<String> =
        lastSeen.filter { (_, ts) -> nowMs - ts <= staleThresholdMs }.keys.sorted()
}
