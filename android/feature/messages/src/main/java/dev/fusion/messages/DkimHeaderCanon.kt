// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

/**
 * Clean-room RFC 6376 §3.4 DKIM (`DomainKeys Identified Mail`) header & body canonicalization
 * and `bh=` SHA-256 body-hash verifier (`DkimHeaderCanon`).
 *
 * Powers `:feature:messages` offline email authenticity inspection:
 * - RFC 6376 §3.4.1 `simple` & §3.4.2 `relaxed` header canonicalization (lowercases header names,
 *   unfolds CRLF WSP continuation lines, compresses WSP runs to single SP, strips WSP around `:`).
 * - RFC 6376 §3.4.3 `simple` & §3.4.4 `relaxed` body canonicalization (ignores trailing empty lines,
 *   ensures exact single trailing `\r\n` on non-empty bodies, `relaxed` strips trailing line WSP and
 *   compresses internal WSP runs).
 * - Parses `DKIM-Signature` tag=value lists (`v=1; a=rsa-sha256; c=relaxed/relaxed; d=...; h=...; bh=...`)
 *   and verifies constant-time equality of `bh=` against `Base64(SHA-256(canonicalBody))`!
 */
object DkimHeaderCanon {
    const val SCOPE =
        "RFC 6376 §3.4 DKIM simple & relaxed header/body canonicalizer + DKIM-Signature tag parser + constant-time SHA-256 bh= body-hash verifier; DNS TXT key fetch out of scope."

    enum class Mode { SIMPLE, RELAXED }

    data class DkimTags(
        val version: String,
        val algorithm: String,
        val headerMode: Mode,
        val bodyMode: Mode,
        val domain: String,
        val signedHeaders: List<String>,
        val bodyHashBase64: String,
    )

    fun canonicalizeHeader(rawHeaderLine: String, mode: Mode): String {
        val crlfCleaned = rawHeaderLine.trimEnd('\r', '\n')
        if (mode == Mode.SIMPLE) return "$crlfCleaned\r\n"

        val colon = crlfCleaned.indexOf(':')
        require(colon > 0) { "Malformed RFC 5322 header (missing ':'): $rawHeaderLine" }
        val name = crlfCleaned.substring(0, colon).trim().lowercase(Locale.ROOT)
        // Unfold CRLF + WSP, then collapse WSP runs (`SP` / `HTAB`) to single `SP` and trim ends.
        val unfoldedVal = crlfCleaned.substring(colon + 1)
            .replace("\r\n ", " ")
            .replace("\r\n\t", " ")
            .replace("\n ", " ")
            .replace("\n\t", " ")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
        return "$name:$unfoldedVal\r\n"
    }

    fun canonicalizeBody(rawBody: String, mode: Mode): String {
        val norm = rawBody.replace("\r\n", "\n").replace('\r', '\n')
        val lines = norm.split('\n').toMutableList()
        if (lines.isNotEmpty() && lines.last().isEmpty()) {
            lines.removeAt(lines.lastIndex)
        }

        val processed = if (mode == Mode.SIMPLE) {
            lines
        } else {
            lines.map { ln ->
                ln.trimEnd(' ', '\t').replace(Regex("[ \\t]+"), " ")
            }
        }.toMutableList()

        // RFC 6376 §3.4.3 / §3.4.4: discard all empty lines at the end of the message body.
        while (processed.isNotEmpty() && processed.last().isEmpty()) {
            processed.removeAt(processed.lastIndex)
        }

        return if (processed.isEmpty()) {
            if (mode == Mode.SIMPLE) "\r\n" else ""
        } else {
            processed.joinToString("\r\n") + "\r\n"
        }
    }

    fun computeBodyHashBase64(rawBody: String, mode: Mode): String {
        val canon = canonicalizeBody(rawBody, mode)
        val digest = MessageDigest.getInstance("SHA-256").digest(canon.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    fun parseDkimSignatureValue(tagList: String): DkimTags {
        val map = linkedMapOf<String, String>()
        for (part in tagList.replace("\r", "").replace("\n", "").split(';')) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val eq = trimmed.indexOf('=')
            require(eq > 0) { "Malformed DKIM tag=value: $trimmed" }
            val k = trimmed.substring(0, eq).trim()
            val v = trimmed.substring(eq + 1).trim()
            require(!map.containsKey(k)) { "RFC 6376 §3.2 forbids duplicate DKIM tag '$k'" }
            map[k] = v
        }
        val ver = map["v"] ?: throw IllegalArgumentException("Missing DKIM v= tag")
        require(ver == "1") { "Unsupported DKIM version v=$ver" }
        val alg = map["a"]?.lowercase(Locale.ROOT) ?: throw IllegalArgumentException("Missing DKIM a= tag")
        require(alg == "rsa-sha256" || alg == "ed25519-sha256") { "Unsupported/weak DKIM algorithm: $alg" }
        val canonSpec = (map["c"] ?: "simple/simple").lowercase(Locale.ROOT).split('/')
        val hMode = parseMode(canonSpec[0])
        val bMode = parseMode(canonSpec.getOrElse(1) { "simple" })
        val dom = map["d"] ?: throw IllegalArgumentException("Missing DKIM d= tag")
        val hdrs = (map["h"] ?: throw IllegalArgumentException("Missing DKIM h= tag"))
            .split(':')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
        require("from" in hdrs) { "RFC 6376 §5.4 requires 'From' header to be signed" }
        val bh = (map["bh"] ?: throw IllegalArgumentException("Missing DKIM bh= tag")).replace(" ", "").replace("\t", "")
        return DkimTags(ver, alg, hMode, bMode, dom, hdrs, bh)
    }

    fun verifyBodyHash(tags: DkimTags, rawBody: String): Boolean {
        val actual = computeBodyHashBase64(rawBody, tags.bodyMode).toByteArray(StandardCharsets.US_ASCII)
        val expected = tags.bodyHashBase64.toByteArray(StandardCharsets.US_ASCII)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun parseMode(s: String): Mode = when (s) {
        "simple" -> Mode.SIMPLE
        "relaxed" -> Mode.RELAXED
        else -> throw IllegalArgumentException("Unknown DKIM canonicalization mode: $s")
    }
}
