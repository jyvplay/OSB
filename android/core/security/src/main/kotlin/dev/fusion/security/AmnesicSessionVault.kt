// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Lightweight Tails-Inspired Amnesic Ephemeral Vault, Tor SOCKS5h Guard & Metadata Scrubber (`:core:security`).
 *
 * Architectural design & honest threat model:
 * - Whereas Tails 7.x boots a 1.5+ GiB compressed live image requiring >= 3,072 MiB system RAM and Linux kernel
 *   `netfilter` + `tmpfs` wipe hooks, an unprivileged Android app cannot control kernel swap/zRAM pages or system-wide
 *   `iptables` without OS cooperation.
 * - Within the Android user-space boundary, `AmnesicSessionVault`:
 *   1. Accepts ZERO `File` / `Path` / `SharedPreferences` sinks by construction (RAM-only `4 MiB` ceiling).
 *   2. Encrypts every in-RAM slot under an ephemeral per-epoch 256-bit AES-GCM key (`96`-bit random IV, `128`-bit tag)
 *      bound with AAD `epoch:<epoch>|slot:<id>` so cross-epoch or cross-slot replay fails authentication.
 *   3. Executes multi-pass volatile XOR read-back zeroization on `panicWipe()` (triggered on screen-off, IME change,
 *      USB HID quarantine, or Quick Panel wipe) and advances `epoch` so stale handles fail closed immediately.
 */
class AmnesicSessionVault(
    val maxTotalPlaintextBytes: Int = DEFAULT_MAX_BYTES,
    private val rng: SecureRandom = SecureRandom()
) {
    companion object {
        const val DEFAULT_MAX_BYTES = 4 * 1024 * 1024 // 4 MiB RAM budget vs 3 GiB Tails 7.x live OS floor
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }

    init {
        require(maxTotalPlaintextBytes in 1024..(16 * 1024 * 1024)) { "Invalid RAM vault cap" }
    }

    private data class SealedSlot(val iv: ByteArray, val ciphertext: ByteArray, val plainLength: Int)

    private var masterKey = ByteArray(32).also { rng.nextBytes(it) }
    private val slots = LinkedHashMap<String, SealedSlot>()
    @Volatile private var compilerFence: Int = 0

    var epoch: Long = 1L
        private set

    val activeSlotCount: Int get() = slots.size
    val totalStoredBytes: Int get() = slots.values.sumOf { it.plainLength }

    private fun aad(slotId: String, targetEpoch: Long): ByteArray =
        "epoch:$targetEpoch|slot:$slotId".toByteArray(StandardCharsets.UTF_8)

    private fun wipeBytes(buf: ByteArray) {
        for (i in buf.indices) buf[i] = 0xAA.toByte()
        var acc = 0
        for (b in buf) acc = acc xor b.toInt()
        for (i in buf.indices) buf[i] = 0x00
        for (b in buf) acc = acc xor b.toInt()
        compilerFence = compilerFence xor acc
    }

    @Synchronized
    fun putSecret(slotId: String, plaintext: ByteArray, wipeCallerArray: Boolean = true): Long {
        require(slotId.matches(Regex("[a-zA-Z0-9._-]{1,64}"))) { "Invalid slot identifier" }
        require(plaintext.size <= maxTotalPlaintextBytes) { "Secret exceeds amnesic RAM ceiling" }
        val existing = slots[slotId]?.plainLength ?: 0
        require(totalStoredBytes - existing + plaintext.size <= maxTotalPlaintextBytes) {
            "Amnesic RAM vault capacity exceeded"
        }
        val iv = ByteArray(GCM_IV_BYTES).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(masterKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(slotId, epoch))
        val sealed = cipher.doFinal(plaintext)
        slots.remove(slotId)?.let { old -> wipeBytes(old.iv); wipeBytes(old.ciphertext) }
        slots[slotId] = SealedSlot(iv, sealed, plaintext.size)
        if (wipeCallerArray) wipeBytes(plaintext)
        return epoch
    }

    @Synchronized
    fun readSecret(slotId: String, expectedEpoch: Long = epoch): ByteArray {
        require(expectedEpoch == epoch) { "Stale amnesic epoch ($expectedEpoch != $epoch): secret invalidated" }
        val entry = requireNotNull(slots[slotId]) { "Missing or wiped amnesic slot: $slotId" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(masterKey, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, entry.iv))
        cipher.updateAAD(aad(slotId, epoch))
        return cipher.doFinal(entry.ciphertext)
    }

    @Synchronized
    fun panicWipe(): Long {
        for (entry in slots.values) {
            wipeBytes(entry.iv)
            wipeBytes(entry.ciphertext)
        }
        slots.clear()
        wipeBytes(masterKey)
        masterKey = ByteArray(32).also { rng.nextBytes(it) }
        check(epoch < Long.MAX_VALUE)
        epoch += 1L
        return epoch
    }

    /** Test inspection hook confirming internal ciphertext/key buffers hold zero plaintext substrings. */
    @Synchronized
    fun containsRawSubsequence(needle: ByteArray): Boolean {
        if (needle.isEmpty()) return false
        fun has(haystack: ByteArray): Boolean {
            if (haystack.size < needle.size) return false
            for (i in 0..(haystack.size - needle.size)) {
                var match = true
                for (j in needle.indices) if (haystack[i + j] != needle[j]) { match = false; break }
                if (match) return true
            }
            return false
        }
        return has(masterKey) || slots.values.any { has(it.iv) || has(it.ciphertext) }
    }
}

/**
 * Fail-closed Tor / SOCKS5h Remote-DNS Transport Guard.
 *
 * Refuses `socks5://` (which leaks DNS to the local resolver before proxy transit), non-loopback untrusted proxy
 * endpoints, cleartext `http://` on clearnet hosts, embedded userinfo credentials, and malformed v3 `.onion` addresses.
 */
object TorTransportPolicy {
    private val V3_ONION_HOST = Regex("^[a-z2-7]{56}\\.onion$")
    private val LOOPBACK_SOCKS5H = Regex("^socks5h://(127\\.0\\.0\\.1|\\[::1\\]):([1-9][0-9]{1,4})$")

    data class RouteDecision(
        val admitted: Boolean,
        val proxyEndpoint: String?,
        val remoteDnsEnforced: Boolean,
        val isOnionV3: Boolean,
        val reason: String
    )

    fun evaluate(targetUrl: String, socksProxyUri: String?, strictOnionOnly: Boolean = false): RouteDecision {
        if (targetUrl.isBlank() || targetUrl.length > 2048 || targetUrl.any { it.code !in 0x21..0x7E }) {
            return RouteDecision(false, null, false, false, "Malformed or unbounded URL")
        }
        val proxyMatch = socksProxyUri?.let { LOOPBACK_SOCKS5H.matchEntire(it.trim()) }
            ?: return RouteDecision(
                false,
                null,
                false,
                false,
                "Fail-closed: local Tor `socks5h://127.0.0.1:<port>` proxy required (`socks5://` local DNS is refused)"
            )
        val port = proxyMatch.groupValues[2].toInt()
        if (port !in 1..65535) return RouteDecision(false, null, false, false, "Invalid SOCKS5h port")

        val parsed = runCatching { java.net.URI(targetUrl) }.getOrElse {
            return RouteDecision(false, null, false, false, "Invalid URI syntax")
        }
        if (parsed.userInfo != null) {
            return RouteDecision(false, null, false, false, "Credentials in URL authority are refused")
        }
        val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: ""
        val host = parsed.host?.lowercase(Locale.ROOT)
            ?: return RouteDecision(false, null, false, false, "Explicit hostname required")
        val onion = V3_ONION_HOST.matches(host)
        if (host.endsWith(".onion") && !onion) {
            return RouteDecision(false, null, false, false, "Only 56-character v3 `.onion` services are admitted (v2 deprecated)")
        }
        if (strictOnionOnly && !onion) {
            return RouteDecision(false, null, false, false, "Strict onion-only mode refuses clearnet exit host: $host")
        }
        if (!onion && scheme != "https") {
            return RouteDecision(false, null, false, false, "Clearnet transport over Tor requires HTTPS end-to-end TLS")
        }
        if (onion && scheme !in setOf("http", "https")) {
            return RouteDecision(false, null, false, false, "Unsupported scheme for v3 onion service: $scheme")
        }
        return RouteDecision(
            admitted = true,
            proxyEndpoint = proxyMatch.value,
            remoteDnsEnforced = true,
            isOnionV3 = onion,
            reason = "Admitted via loopback SOCKS5h with remote DNS resolution"
        )
    }
}

/**
 * Clean-copy Metadata & Trojan-Source Scrubber for text and PDF byte streams.
 *
 * `scrubPdfMetadata` replaces `/Author`, `/Creator`, `/Producer`, `/Subject`, `/Keywords`, `/CreationDate`, `/ModDate`
 * literal parenthesized values and `<x:xmpmeta ... </x:xmpmeta>` XML packets with exact-byte-length ASCII space (`0x20`)
 * padding so PDF `xref` byte offsets remain 100% intact without re-rendering or rasterizing text.
 */
object MetadataScrubber {
    private val BIDI_AND_INVISIBLE = setOf(
        '\u200B', '\u200C', '\u200D', '\u200E', '\u200F',
        '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
        '\u2060', '\u2066', '\u2067', '\u2068', '\u2069',
        '\uFEFF'
    )

    data class TextScrubReport(val cleanText: String, val removedCodePoints: Int)
    data class PdfScrubReport(val bytes: ByteArray, val scrubbedFields: List<String>, val scrubbedXmpPackets: Int)

    fun scrubText(input: String): TextScrubReport {
        var removed = 0
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in BIDI_AND_INVISIBLE || (ch.isISOControl() && ch != '\n' && ch != '\r' && ch != '\t')) {
                removed++
            } else {
                sb.append(ch)
            }
        }
        return TextScrubReport(sb.toString(), removed)
    }

    fun scrubPdfMetadata(pdfBytes: ByteArray): PdfScrubReport {
        require(pdfBytes.size in 8..(32 * 1024 * 1024)) { "PDF outside 8B..32MiB bounds" }
        val header = String(pdfBytes.copyOfRange(0, minOf(16, pdfBytes.size)), StandardCharsets.ISO_8859_1)
        require(header.startsWith("%PDF-")) { "Not a valid `%PDF-` header" }

        val work = pdfBytes.copyOf()
        val latin1 = String(work, StandardCharsets.ISO_8859_1)
        val keys = listOf("Author", "Creator", "Producer", "Subject", "Keywords", "CreationDate", "ModDate")
        val scrubbed = ArrayList<String>()

        for (key in keys) {
            val pattern = Regex("/$key\\s*\\((?:\\\\.|[^\\\\)])*\\)")
            for (match in pattern.findAll(latin1)) {
                val openParen = latin1.indexOf('(', match.range.first)
                val closeParen = match.range.last
                if (openParen in 0 until closeParen) {
                    for (idx in (openParen + 1) until closeParen) {
                        work[idx] = 0x20 // preserve exact PDF xref byte offsets
                    }
                    scrubbed.add(key)
                }
            }
        }

        var xmpCount = 0
        val xmpPattern = Regex("<x:xmpmeta\\b[\\s\\S]*?</x:xmpmeta>")
        for (match in xmpPattern.findAll(latin1)) {
            for (idx in match.range) {
                work[idx] = 0x20
            }
            xmpCount++
        }

        check(work.size == pdfBytes.size) { "PDF length invariant violated" }
        return PdfScrubReport(work, Collections.unmodifiableList(scrubbed.distinct()), xmpCount)
    }
}
