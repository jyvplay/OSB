// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

/**
 * Bounded collaborative presence tracker. Tracks user presence (online,
 * away, offline) with a last-seen timestamp per user. Users are bounded in
 * count; user ids are bounded in length; timestamps are non-negative
 * milliseconds. Presence transitions are recorded but the tracker does
 * not manage websockets or push notifications — the transport layer
 * supplies events.
 *
 * Declared narrowings: at most [MAX_USERS] users; user ids at most
 * [MAX_ID_LEN] chars matching [ID_RE]; timestamps non-negative and at most
 * [MAX_TS]; offline users are retained until explicitly removed.
 */
class PresenceTracker private constructor(
    private val presence: LinkedHashMap<String, PresenceRecord>
) {
    companion object {
        const val MAX_USERS = 1024
        const val MAX_ID_LEN = 128
        const val MAX_TS = Long.MAX_VALUE
        private val ID_RE = Regex("[a-zA-Z0-9_-]{1,64}")

        data class PresenceRecord(
            val status: String,   // "online", "away", "offline"
            val lastSeenMs: Long
        )

        data class Snapshot(
            val users: List<String>,
            val onlineCount: Int,
            val awayCount: Int,
            val offlineCount: Int
        )

        fun empty(): PresenceTracker = PresenceTracker(LinkedHashMap())
    }

    fun setPresence(userId: String, status: String, lastSeenMs: Long) {
        require(ID_RE.matches(userId)) { "invalid user id: $userId" }
        require(userId.length <= MAX_ID_LEN) { "user id too long" }
        require(status in setOf("online", "away", "offline")) { "invalid status: $status" }
        require(lastSeenMs >= 0 && lastSeenMs <= MAX_TS) { "timestamp out of bounds" }
        require(presence.size < MAX_USERS || presence.containsKey(userId)) {
            "too many users: $MAX_USERS"
        }
        presence[userId] = PresenceRecord(status, lastSeenMs)
    }

    fun removeUser(userId: String): Boolean = presence.remove(userId) != null

    fun presence(userId: String): PresenceRecord? = presence[userId]

    fun snapshot(): Snapshot {
        val users = presence.keys.toList()
        var online = 0; var away = 0; var offline = 0
        for (r in presence.values) {
            when (r.status) {
                "online" -> online++
                "away" -> away++
                "offline" -> offline++
            }
        }
        return Snapshot(users, online, away, offline)
    }

    fun userCount(): Int = presence.size
    fun clear() = presence.clear()
}
