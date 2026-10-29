// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room Tor Proposal 351 (`<torS0X>0`) SOCKS5 stream-isolation & DNS-leak-proof framing engine
 * (`TorSocksIsolation`), modeled after Tor 0.4.9.1+ / Arti 1.2.8+ (`socks-extensions.md` & `prop351`).
 *
 * Enforces three Tails-class anonymity invariants:
 * 1. **Tor Proposal 351 SOCKS5 Extended Authentication**:
 *    - Username MUST equal exact 9-byte ASCII `<torS0X>0` (`8`-byte magic `<torS0X>` + format byte `'0'`).
 *    - Per Proposal 351 responder rules, any username starting with `<torS0X>` with a suffix other than `"0"`
 *      MUST be rejected fail-closed so future protocol extensions never silently downgrade stream isolation!
 *    - Password holds a 32-byte (`64`-hex-char) `HMAC-SHA256` stream-isolation credential derived from
 *      `(sessionSecret, appId, firstPartyDomain, tabEpoch)` so every `(app, first-party site)` pair gets
 *      a cryptographically unlinkable Tor circuit!
 * 2. **Zero Local DNS Leak (`ATYP = 0x03` Domain Name)**:
 *    - Hostnames (including `.onion` v3 56-char base32 addresses) are encoded directly into the SOCKS5
 *      `CONNECT` frame (`VER=0x05, CMD=0x01, RSV=0x00, ATYP=0x03`) without calling `InetAddress.getByName`.
 *    - Raw IPv4/IPv6 literals are optionally refused in strict `.onion`/domain-only mode to defeat WebRTC/IP leaks.
 * 3. **Proposal 368 Isolated Circuit Expiration**:
 *    - Tracks active vs idle isolated stream tokens and expires idle tokens after `maxIdleMs`.
 */
object TorSocksIsolation {
    const val SCOPE =
        "Tor Proposal 351 (<torS0X>0) SOCKS5 RFC 1928/1929 stream-isolation auth + ATYP=0x03 zero-DNS-leak CONNECT framing + Prop 368 circuit expiry; Tor cell-layer onion encryption out of scope."

    const val PROP351_MAGIC = "<torS0X>"
    const val PROP351_USER_V0 = "<torS0X>0"
    const val SOCKS_VER: Byte = 0x05
    const val AUTH_USERPASS: Byte = 0x02
    const val AUTH_VER_RFC1929: Byte = 0x01
    const val CMD_CONNECT: Byte = 0x01
    const val ATYP_DOMAIN: Byte = 0x03

    private val ONION_V3_RE = Regex("^[a-z2-7]{56}\\.onion$")
    private val IPV4_RE = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")

    data class StreamContext(
        val appId: String,
        val firstPartyOrigin: String,
        val tabEpoch: Long = 0L,
    ) {
        init {
            require(appId.isNotBlank()) { "appId must not be blank" }
            require(firstPartyOrigin.isNotBlank()) { "firstPartyOrigin must not be blank" }
        }
    }

    data class SocksCredentials(
        val username: String,
        val passwordHex: String,
    ) {
        init {
            require(username.toByteArray(StandardCharsets.US_ASCII).size in 1..255) { "RFC 1929 ULEN out of range" }
            require(passwordHex.toByteArray(StandardCharsets.US_ASCII).size in 1..255) { "RFC 1929 PLEN out of range" }
        }
    }

    fun deriveProp351Credentials(sessionSecret: ByteArray, ctx: StreamContext): SocksCredentials {
        require(sessionSecret.size >= 16) { "sessionSecret must be >= 16 bytes" }
        val normalizedOrigin = ctx.firstPartyOrigin.trim().lowercase(Locale.ROOT)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sessionSecret, "HmacSHA256"))
        val msg = "tor-prop351-v0|${ctx.appId.trim()}|$normalizedOrigin|${ctx.tabEpoch}"
            .toByteArray(StandardCharsets.UTF_8)
        val tag = mac.doFinal(msg)
        val hex = buildString(64) {
            for (b in tag) {
                val v = b.toInt() and 0xFF
                append("0123456789abcdef"[v ushr 4])
                append("0123456789abcdef"[v and 0x0F])
            }
        }
        return SocksCredentials(PROP351_USER_V0, hex)
    }

    /**
     * Validates incoming/outgoing SOCKS5 RFC 1929 credentials against Tor Proposal 351 rules:
     * - If `username` begins with `<torS0X>`, ONLY exact `<torS0X>0` is valid; any other `<torS0X>*`
     *   version/extension MUST fail closed!
     */
    fun validateProp351Auth(username: String, password: String): Boolean {
        if (username.startsWith(PROP351_MAGIC)) {
            if (username != PROP351_USER_V0) return false
            return password.isNotEmpty() && password.toByteArray(StandardCharsets.UTF_8).size <= 255
        }
        return username.isNotEmpty() && username.toByteArray(StandardCharsets.UTF_8).size <= 255 &&
            password.toByteArray(StandardCharsets.UTF_8).size <= 255
    }

    fun buildGreetingFrame(): ByteArray = byteArrayOf(SOCKS_VER, 0x01, AUTH_USERPASS)

    fun buildRfc1929AuthFrame(creds: SocksCredentials): ByteArray {
        require(validateProp351Auth(creds.username, creds.passwordHex)) {
            "Invalid Tor Proposal 351 SOCKS5 credentials for username '${creds.username}'"
        }
        val u = creds.username.toByteArray(StandardCharsets.US_ASCII)
        val p = creds.passwordHex.toByteArray(StandardCharsets.US_ASCII)
        val out = ByteArray(3 + u.size + p.size)
        out[0] = AUTH_VER_RFC1929
        out[1] = u.size.toByte()
        System.arraycopy(u, 0, out, 2, u.size)
        out[2 + u.size] = p.size.toByte()
        System.arraycopy(p, 0, out, 3 + u.size, p.size)
        return out
    }

    fun buildDomainConnectFrame(
        host: String,
        port: Int,
        forbidRawIpLiterals: Boolean = true,
    ): ByteArray {
        require(port in 1..65535) { "Invalid TCP port: $port" }
        val cleanHost = host.trim().lowercase(Locale.ROOT).trimEnd('.')
        require(cleanHost.isNotEmpty() && !cleanHost.contains('\u0000') && !cleanHost.contains('/')) {
            "Invalid SOCKS5 target hostname: $host"
        }
        if (forbidRawIpLiterals) {
            require(!IPV4_RE.matches(cleanHost) && !cleanHost.contains(':')) {
                "Raw IP literal '$cleanHost' refused in strict Tor domain-isolation mode"
            }
        }
        if (cleanHost.endsWith(".onion")) {
            require(ONION_V3_RE.matches(cleanHost)) {
                "Invalid or deprecated v2 .onion address (56-char v3 base32 required): $cleanHost"
            }
        }
        val hostBytes = cleanHost.toByteArray(StandardCharsets.US_ASCII)
        require(hostBytes.size in 1..255) { "Domain exceeds SOCKS5 255-octet limit" }
        val frame = ByteArray(7 + hostBytes.size)
        frame[0] = SOCKS_VER
        frame[1] = CMD_CONNECT
        frame[2] = 0x00 // RSV
        frame[3] = ATYP_DOMAIN
        frame[4] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, frame, 5, hostBytes.size)
        frame[5 + hostBytes.size] = ((port ushr 8) and 0xFF).toByte()
        frame[6 + hostBytes.size] = (port and 0xFF).toByte()
        return frame
    }

    class CircuitExpirationTracker(val maxIdleMs: Long = 600_000L) {
        private val lastActiveMs = linkedMapOf<String, Long>()
        private val openStreams = linkedMapOf<String, Int>()

        fun onStreamOpened(passwordHex: String, nowMs: Long) {
            openStreams[passwordHex] = (openStreams[passwordHex] ?: 0) + 1
            lastActiveMs[passwordHex] = nowMs
        }

        fun onStreamClosed(passwordHex: String, nowMs: Long) {
            val rem = ((openStreams[passwordHex] ?: 1) - 1).coerceAtLeast(0)
            openStreams[passwordHex] = rem
            lastActiveMs[passwordHex] = nowMs
        }

        fun evictExpiredTokens(nowMs: Long): Set<String> {
            val evicted = linkedSetOf<String>()
            val iter = lastActiveMs.entries.iterator()
            while (iter.hasNext()) {
                val (tok, ts) = iter.next()
                val active = openStreams[tok] ?: 0
                if (active == 0 && nowMs - ts >= maxIdleMs) {
                    evicted.add(tok)
                    openStreams.remove(tok)
                    iter.remove()
                }
            }
            return evicted
        }
    }
}
