// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * Clean-room Terminal 24-bit TrueColor SGR Parser, Bracketed Safe-Paste Injection Sanitizer (`TD-01`),
 * and OpenSSH `known_hosts` Ed25519 Host-Key Pin Verifier (`TD-03`).
 *
 * Defends against clipboard-paste terminal command-execution attacks (`curl ... | sh\n` hidden via CSS
 * on malicious web pages or `ESC ] 52` OSC clipboard / `ESC [ 201 ~` premature bracketed-paste breakout!):
 * - Strips all `ESC` (`0x1B`), `CSI` (`0x9B`), `NUL`, `BEL`, `DEL`, and C0 control bytes except `\t` and `\n`.
 * - Flags multi-line `\n`/`\r` pastes as `requiresExplicitMultiLineConfirm = true` before sending to PTY!
 * - Wraps approved pastes cleanly inside `ESC[200~ ... ESC[201~` when bracketed paste mode is active.
 */
object VtTrueColorSafePasteEngine {
    data class TrueColorStyle(
        val fgRgb24: Int?, // null = default fg
        val bgRgb24: Int?, // null = default bg
        val bold: Boolean = false,
        val underline: Boolean = false,
    )

    data class SafePastePlan(
        val sanitizedText: String,
        val wireBytesForPty: ByteArray,
        val strippedDangerousChars: Int,
        val containsNewlinesConfirmRequired: Boolean,
    )

    fun parseSgrSequence(params: List<Int>, initial: TrueColorStyle = TrueColorStyle(null, null)): TrueColorStyle {
        if (params.isEmpty()) return TrueColorStyle(null, null)
        var cur = initial
        var i = 0
        while (i < params.size) {
            when (val code = params[i]) {
                0 -> cur = TrueColorStyle(null, null, bold = false, underline = false)
                1 -> cur = cur.copy(bold = true)
                4 -> cur = cur.copy(underline = true)
                22 -> cur = cur.copy(bold = false)
                24 -> cur = cur.copy(underline = false)
                39 -> cur = cur.copy(fgRgb24 = null)
                49 -> cur = cur.copy(bgRgb24 = null)
                38 -> if (i + 4 < params.size && params[i + 1] == 2) {
                    val rgb = ((params[i + 2].coerceIn(0, 255)) shl 16) or
                        ((params[i + 3].coerceIn(0, 255)) shl 8) or
                        (params[i + 4].coerceIn(0, 255))
                    cur = cur.copy(fgRgb24 = rgb)
                    i += 4
                }
                48 -> if (i + 4 < params.size && params[i + 1] == 2) {
                    val rgb = ((params[i + 2].coerceIn(0, 255)) shl 16) or
                        ((params[i + 3].coerceIn(0, 255)) shl 8) or
                        (params[i + 4].coerceIn(0, 255))
                    cur = cur.copy(bgRgb24 = rgb)
                    i += 4
                }
            }
            i++
        }
        return cur
    }

    fun prepareSafePaste(rawClipboard: String, bracketedPasteEnabled: Boolean = true): SafePastePlan {
        val sb = StringBuilder(rawClipboard.length)
        var stripped = 0
        var i = 0
        val normalized = rawClipboard.replace("\r\n", "\n").replace('\r', '\n')
        while (i < normalized.length) {
            val ch = normalized[i]
            when {
                // Strip ESC [ ... finalByte or ESC ] ... BEL/ST sequences completely so `ESC[201~` breakout is impossible!
                ch == '\u001B' -> {
                    stripped++
                    i++
                    if (i < normalized.length && normalized[i] == '[') {
                        i++
                        while (i < normalized.length && normalized[i].code !in 0x40..0x7E) { stripped++; i++ }
                        if (i < normalized.length) { stripped++; i++ }
                    } else if (i < normalized.length && normalized[i] == ']') {
                        i++
                        while (i < normalized.length && normalized[i] != '\u0007' && normalized[i] != '\u001B') { stripped++; i++ }
                        if (i < normalized.length && normalized[i] == '\u0007') { stripped++; i++ }
                    }
                    continue
                }
                ch == '\n' || ch == '\t' -> sb.append(ch)
                ch.code < 0x20 || ch.code == 0x7F || ch.code in 0x80..0x9F ||
                    ch.code in 0x202A..0x202E || ch.code in 0x2066..0x2069 || ch == '\u061C' -> stripped++
                else -> sb.append(ch)
            }
            i++
        }
        val clean = sb.toString()
        val hasNewline = '\n' in clean
        val framed = if (bracketedPasteEnabled) "\u001B[200~$clean\u001B[201~" else clean
        return SafePastePlan(
            sanitizedText = clean,
            wireBytesForPty = framed.toByteArray(StandardCharsets.UTF_8),
            strippedDangerousChars = stripped,
            containsNewlinesConfirmRequired = hasNewline,
        )
    }

    enum class HostKeyVerdict { MATCHED_PINNED_HOST, FIRST_USE_UNPINNED, MITM_HOST_KEY_CHANGED }

    fun verifySshKnownHost(
        knownHostsContent: String,
        hostPort: String,
        serverKeyAlgorithm: String,
        serverPublicKeyBlob: ByteArray,
    ): Pair<HostKeyVerdict, String> {
        val sha256B64 = Base64.getEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(serverPublicKeyBlob))
        val fingerprint = "SHA256:$sha256B64"
        val presentedB64 = Base64.getEncoder().encodeToString(serverPublicKeyBlob)

        var sawHost = false
        for (rawLine in knownHostsContent.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val cols = line.split(Regex("\\s+"))
            if (cols.size < 3) continue
            val hosts = cols[0].split(',').map { it.trim().lowercase() }
            if (hostPort.trim().lowercase() in hosts) {
                sawHost = true
                if (cols[1] == serverKeyAlgorithm && cols[2] == presentedB64) {
                    return HostKeyVerdict.MATCHED_PINNED_HOST to fingerprint
                }
            }
        }
        return if (sawHost) {
            HostKeyVerdict.MITM_HOST_KEY_CHANGED to fingerprint
        } else {
            HostKeyVerdict.FIRST_USE_UNPINNED to fingerprint
        }
    }
}
