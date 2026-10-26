// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.util.Collections

/**
 * Tails-inspired amnesiac session policy, as a pure state machine.
 *
 * Honest scope: this models WHICH data categories may persist and WHEN volatile state must
 * be discarded. It is not an operating system, not RAM erasure, not a paging guarantee, and
 * not anonymity. Forensic-erasure, swap, hibernation and host/firmware observation remain
 * outside what an app-level policy can prove. See R26-PRIVACY-PRD.
 */
object AmnesiaPolicy {
    /** Categories mirror Tails Persistent Storage granularity: opt-in per category, never wholesale. */
    enum class Category(val title: String, val persistable: Boolean) {
        DOCUMENTS("Saved documents", true),
        BOOKMARKS("Bookmarks and pins", true),
        NETWORK_IDENTITY("Tor bridges and network settings", true),
        MEDIA_CACHE("Downloaded media", true),
        INPUT_SCRATCH("Typed secrets and scratch input", false),
    }

    enum class Session { FRESH, UNLOCKED, LOCKED, WIPED }

    class Store private constructor(
        val session: Session,
        enabled: Set<Category>,
        val unlocks: Long,
        val wipes: Long,
    ) {
        val enabled: Set<Category> = Collections.unmodifiableSet(HashSet(enabled))
        init {
            require(unlocks in 0 until Long.MAX_VALUE && wipes in 0 until Long.MAX_VALUE)
            require(Category.INPUT_SCRATCH !in enabled) { "Scratch input is never persistable" }
            require(session != Session.FRESH || enabled.isEmpty()) { "A fresh session starts with no persistence" }
            require(session != Session.WIPED || enabled.isEmpty()) { "A wiped session retains no persistence" }
        }

        /** Unlocking with an empty passphrase boots amnesic: no categories enabled, never an error. */
        fun unlock(passphrase: String): Store {
            require(session == Session.FRESH || session == Session.LOCKED) { "Unlock applies to fresh/locked sessions" }
            require(passphrase.length <= 512)
            check(unlocks < Long.MAX_VALUE - 1)
            return Store(Session.UNLOCKED, if (passphrase.isEmpty()) emptySet() else enabled, unlocks + 1, wipes)
        }

        /** Explicit per-category opt-in. No bulk "persist everything" transition exists by design. */
        fun enable(category: Category): Store {
            require(session == Session.UNLOCKED) { "Categories unlock only in an unlocked session" }
            require(category.persistable) { "${category.title} can never persist" }
            if (category in enabled) return this
            return Store(session, enabled + category, unlocks, wipes)
        }

        fun disable(category: Category): Store {
            if (category !in enabled) return this
            return Store(session, enabled - category, unlocks, wipes)
        }

        fun lock(): Store {
            require(session == Session.UNLOCKED) { "Only an unlocked session locks" }
            return Store(Session.LOCKED, enabled, unlocks, wipes)
        }

        /** Wipe discards volatile holdings and clears persistence grants. The receipt names every category handled. */
        fun wipe(): Pair<Store, WipeReceipt> {
            require(session != Session.WIPED) { "Already wiped" }
            check(wipes < Long.MAX_VALUE - 1)
            val receipt = WipeReceipt(
                volatileDiscarded = (Category.entries.toSet() - enabled) + Category.INPUT_SCRATCH,
                grantsCleared = enabled.toSet(),
            )
            return Store(Session.WIPED, emptySet(), unlocks, wipes + 1) to receipt
        }

        fun mayPersist(category: Category): Boolean = session == Session.UNLOCKED && category in enabled

        override fun equals(other: Any?) = other is Store && session == other.session && enabled == other.enabled &&
            unlocks == other.unlocks && wipes == other.wipes
        override fun hashCode() = listOf(session, enabled, unlocks, wipes).hashCode()

        companion object {
            fun fresh() = Store(Session.FRESH, emptySet(), 0, 0)
        }
    }

    data class WipeReceipt(val volatileDiscarded: Set<Category>, val grantsCleared: Set<Category>) {
        init {
            require(Category.INPUT_SCRATCH in volatileDiscarded) { "Scratch input must always be discarded" }
            require(volatileDiscarded.intersect(grantsCleared).isEmpty()) { "A category is either volatile or cleared, never both" }
        }
        /** Plain-language receipt. It reports policy transitions, not forensic erasure. */
        fun disclosure(): String = buildString {
            append("Session wiped. Discarded volatile: ")
            append(volatileDiscarded.sortedBy { it.name }.joinToString(", ") { it.title })
            append(if (grantsCleared.isEmpty()) ". No persistence grants were active."
            else ". Cleared persistence grants: " + grantsCleared.sortedBy { it.name }.joinToString(", ") { it.title })
            append(" This reports app-level discard only; it does not prove RAM, swap, backup or host erasure, and no forensic-erasure claim is made.")
        }
    }
}
