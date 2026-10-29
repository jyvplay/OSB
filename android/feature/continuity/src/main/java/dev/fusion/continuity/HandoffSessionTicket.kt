// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room local-LAN cross-device task handoff ticket (`HandoffSessionTicket`).
 *
 * Authenticates offline peer-to-peer continuity (`Writer` cursor position / `Browser` tab handoff)
 * with `HMAC-SHA256`, short monotonic `ttlMs` (`<= 60_000 ms`), and single-use `nonceHex` anti-replay!
 */
object HandoffSessionTicket {
    const val SCOPE =
        "HMAC-SHA256 authenticated LAN continuity handoff ticket with <=60s TTL & single-use nonce anti-replay cache; mDNS BLE discovery beacons out of scope."

    data class Payload(
        val appRoute: String,
        val documentId: String,
        val scrollOffset: Int,
        val issuedAtMs: Long,
        val expiresAtMs: Long,
        val nonceHex: String,
    )

    fun issue(
        sharedSecret: ByteArray,
        appRoute: String,
        documentId: String,
        scrollOffset: Int,
        nowMs: Long,
        ttlMs: Long = 30_000L,
        nonceHex: String,
    ): String {
        require(sharedSecret.size >= 32) { "Continuity sharedSecret must be >= 32 bytes" }
        require(ttlMs in 1_000L..60_000L) { "Continuity ticket TTL must be in 1..60s" }
        require(!appRoute.contains('|') && !documentId.contains('|') && nonceHex.length >= 16) { "Invalid ticket fields" }
        val exp = nowMs + ttlMs
        val body = "$appRoute|$documentId|$scrollOffset|$nowMs|$exp|$nonceHex"
        val bodyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray(StandardCharsets.UTF_8))
        val tagB64 = hmacB64(sharedSecret, bodyB64)
        return "$bodyB64.$tagB64"
    }

    class Verifier(private val sharedSecret: ByteArray, private val maxConsumedNonces: Int = 512) {
        private val consumedNonces = LinkedHashSet<String>()

        fun verifyAndConsume(token: String, nowMs: Long): Payload {
            val dot = token.indexOf('.')
            require(dot > 0 && dot < token.lastIndex) { "Malformed continuity ticket" }
            val bodyB64 = token.substring(0, dot)
            val sigB64 = token.substring(dot + 1)
            val expectedB64 = hmacB64(sharedSecret, bodyB64)
            require(
                MessageDigest.isEqual(
                    sigB64.toByteArray(StandardCharsets.US_ASCII),
                    expectedB64.toByteArray(StandardCharsets.US_ASCII),
                ),
            ) { "Continuity ticket HMAC verification failed" }

            val parts = String(Base64.getUrlDecoder().decode(bodyB64), StandardCharsets.UTF_8).split('|')
            require(parts.size == 6) { "Invalid continuity payload arity" }
            val issued = parts[3].toLong()
            val expires = parts[4].toLong()
            val nonce = parts[5]
            require(nowMs in issued..expires) { "Continuity ticket expired or from future ($nowMs not in $issued..$expires)" }
            require(consumedNonces.add(nonce)) { "Replayed continuity ticket nonce rejected: $nonce" }
            while (consumedNonces.size > maxConsumedNonces) {
                val oldest = consumedNonces.first()
                consumedNonces.remove(oldest)
            }
            return Payload(parts[0], parts[1], parts[2].toInt(), issued, expires, nonce)
        }
    }

    private fun hmacB64(secret: ByteArray, msgAscii: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val tag = mac.doFinal(msgAscii.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tag)
    }
}
