// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

import java.util.Base64

/**
 * XTerm **OSC 52** (`ESC ] 52 ; c ; <base64> BEL/ST`) terminal clipboard escape firewall blocking
 * read-clipboard queries (`?`), bounding payload size, and stripping hidden bracketed-paste /
 * newline injection attacks (`\r`, `\n`, `ESC`) before allowing terminal clipboard copy!
 */
object TerminalOsc52ClipboardSanitizer {
    const val SCOPE =
        "XTerm OSC 52 terminal clipboard escape parser blocking '?' readback exfiltration and neutralizing paste-injection control characters."

    data class Osc52Decision(
        val permitted: Boolean,
        val sanitizedText: String?,
        val blockedReason: String?,
    )

    fun evaluateOsc52Sequence(rawSequence: String, allowWriteClipboard: Boolean = true, maxDecodedBytes: Int = 16_384): Osc52Decision {
        if (!allowWriteClipboard) return Osc52Decision(false, null, "OSC 52 clipboard write disabled by policy")
        val prefix = "\u001B]52;"
        val body = when {
            rawSequence.startsWith(prefix) && rawSequence.endsWith("\u0007") ->
                rawSequence.substring(prefix.length, rawSequence.length - 1)
            rawSequence.startsWith(prefix) && rawSequence.endsWith("\u001B\\") ->
                rawSequence.substring(prefix.length, rawSequence.length - 2)
            else -> return Osc52Decision(false, null, "Malformed OSC 52 framing")
        }
        val semi = body.indexOf(';')
        if (semi < 0) return Osc52Decision(false, null, "Missing selection parameter in OSC 52")
        val payloadPart = body.substring(semi + 1)
        if (payloadPart == "?") {
            return Osc52Decision(false, null, "OSC 52 '?' clipboard readback exfiltration strictly blocked")
        }
        val decoded = runCatching { Base64.getDecoder().decode(payloadPart) }.getOrNull()
            ?: return Osc52Decision(false, null, "Invalid Base64 in OSC 52")
        if (decoded.size > maxDecodedBytes) {
            return Osc52Decision(false, null, "OSC 52 payload ${decoded.size}B exceeds cap ${maxDecodedBytes}B")
        }
        val text = String(decoded, Charsets.UTF_8)
        // Block hidden shell command-execution paste attacks containing ESC or CR/LF if single-line guard active
        if (text.any { it.code < 0x20 && it != '\t' }) {
            return Osc52Decision(false, null, "Blocked control/newline paste-injection character in OSC 52")
        }
        return Osc52Decision(true, text, null)
    }
}
