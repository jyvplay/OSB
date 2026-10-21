// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.security.SecureRandom

/**
 * R28 EXECUTED host kernel. Scoped single-use consent receipts for sensitive
 * actions (external handoff, export, USB-observed changes). The caller supplies
 * epoch millis (no clock inside, so checks are deterministic); receipts are
 * random 128-bit ids, expire, and burn on first use. Replay, scope mismatch
 * and expiry all refuse. This raises the bar for injected-input attacks: a bare
 * keystroke stream cannot mint a receipt.
 */
class ConsentLedger private constructor(private val receipts: Map<String, Receipt>) {
    data class Receipt(val id: String, val scope: String, val issuedAt: Long, val expiresAt: Long, val used: Boolean)

    companion object {
        const val MAX_OUTSTANDING = 128
        const val MIN_TTL_SECONDS = 30L
        const val MAX_TTL_SECONDS = 3600L
        private val SCOPE = Regex("[a-z0-9][a-z0-9.-]{0,63}")
        private val random = SecureRandom()

        fun empty(): ConsentLedger = ConsentLedger(emptyMap())

        private fun newId(): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    fun outstanding(): Int = receipts.count { !it.value.used }

    /** Issue a receipt for [scope] living [ttlSeconds]. Returns the updated ledger and receipt. */
    fun issue(scope: String, nowEpochMs: Long, ttlSeconds: Long): Pair<ConsentLedger, Receipt> {
        require(SCOPE.matches(scope)) { "Scope must be 1..64 lowercase dot/dash segments" }
        require(nowEpochMs in 0..4102444800000L) { "nowEpochMs out of range" }
        require(ttlSeconds in MIN_TTL_SECONDS..MAX_TTL_SECONDS) { "TTL must be 30..3600 seconds" }
        val live = receipts.filterValues { !it.used && it.expiresAt > nowEpochMs }
        require(live.size < MAX_OUTSTANDING) { "Too many outstanding receipts" }
        val receipt = Receipt(newId(), scope, nowEpochMs, nowEpochMs + ttlSeconds * 1000L, false)
        return ConsentLedger(live + (receipt.id to receipt)) to receipt
    }

    /** Burn [id] for [scope] now, or throw. */
    fun consume(id: String, scope: String, nowEpochMs: Long): ConsentLedger {
        require(nowEpochMs in 0..4102444800000L) { "nowEpochMs out of range" }
        val receipt = receipts[id] ?: throw IllegalArgumentException("Unknown consent receipt")
        check(!receipt.used) { "Consent receipt already used" }
        check(receipt.scope == scope) { "Consent receipt scope mismatch" }
        check(nowEpochMs < receipt.expiresAt) { "Consent receipt expired" }
        return ConsentLedger(receipts + (id to receipt.copy(used = true)))
    }

    /** Drop used and expired receipts. */
    fun prune(nowEpochMs: Long): ConsentLedger {
        require(nowEpochMs in 0..4102444800000L) { "nowEpochMs out of range" }
        return ConsentLedger(receipts.filterValues { !it.used && it.expiresAt > nowEpochMs })
    }
}
