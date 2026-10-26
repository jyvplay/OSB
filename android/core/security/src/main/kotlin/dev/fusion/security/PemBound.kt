// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Strict single-block PEM reader pinned to RFC 7468 (April 2015):
 *
 * - Encapsulation boundaries are "-----BEGIN <label>-----" and
 *   "-----END <label>-----"; the two labels MUST match exactly.
 * - Label grammar (section 3): visible characters where hyphen-minus and
 *   space may appear only between label characters (no leading/trailing
 *   separator, no double separator). Empty labels are allowed by the
 *   ABNF but refused here as a declared narrowing (no registered empty
 *   label exists).
 * - Body is strict base64 (section 3 "strict" producers): lines of
 *   exactly 64 characters except the final line, canonical padding, no
 *   whitespace inside lines.
 * - This reader is the strict "parsers MAY" profile: nothing is allowed
 *   before the BEGIN line or after the END line except a final newline,
 *   and exactly one block must be present.
 *
 * Returns the label and decoded bytes; refusal is
 * IllegalArgumentException. No ASN.1, no keys, no crypto claims.
 */
object PemBound {
    data class Block(val label: String, val bytes: ByteArray) {
        override fun equals(other: Any?) = other is Block && label == other.label && bytes.contentEquals(other.bytes)
        override fun hashCode() = 31 * label.hashCode() + bytes.contentHashCode()
    }

    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun parse(text: String): Block {
        val lines = text.split("\r\n", "\n")
        val content = if (lines.isNotEmpty() && lines.last().isEmpty()) lines.dropLast(1) else lines
        require(content.size >= 2) { "too short for a PEM block" }
        val first = content.first()
        val last = content.last()
        require(first.startsWith("-----BEGIN ") && first.endsWith("-----")) { "missing BEGIN boundary" }
        require(last.startsWith("-----END ") && last.endsWith("-----")) { "missing END boundary" }
        val beginLabel = first.removePrefix("-----BEGIN ").removeSuffix("-----")
        val endLabel = last.removePrefix("-----END ").removeSuffix("-----")
        require(beginLabel == endLabel) { "BEGIN label '$beginLabel' != END label '$endLabel'" }
        checkLabel(beginLabel)
        val body = content.subList(1, content.size - 1)
        for ((i, line) in body.withIndex()) {
            require(!line.contains("-----BEGIN")) { "second PEM block refused" }
            if (i < body.size - 1) {
                require(line.length == 64) { "interior base64 line must be 64 chars, got ${line.length}" }
            } else {
                require(line.isNotEmpty() && line.length <= 64) { "bad final base64 line length" }
            }
        }
        val joined = body.joinToString("")
        return Block(beginLabel, b64decode(joined))
    }

    fun checkLabel(label: String) {
        require(label.isNotEmpty()) { "empty label refused (declared narrowing)" }
        var prevSep = true // treat start as separator so a leading sep refuses
        for (c in label) {
            if (c == ' ' || c == '-') {
                require(!prevSep) { "label separator must sit between label characters" }
                prevSep = true
            } else {
                require(c.code in 0x21..0x7E) { "non-visible label char" }
                prevSep = false
            }
        }
        require(!prevSep) { "label ends with a separator" }
    }

    /** Strict RFC 4648 base64: canonical padding, canonical final quantum. */
    fun b64decode(s: String): ByteArray {
        require(s.isNotEmpty() && s.length % 4 == 0) { "base64 length must be a positive multiple of 4" }
        val pad = s.takeLastWhile { it == '=' }.length
        require(pad <= 2) { "too much padding" }
        val main = s.substring(0, s.length - pad)
        require(main.all { B64.indexOf(it) >= 0 }) { "non-alphabet base64 char" }
        val out = java.io.ByteArrayOutputStream()
        var acc = 0
        var bits = 0
        for (c in main) {
            acc = (acc shl 6) or B64.indexOf(c)
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((acc shr bits) and 0xFF)
            }
        }
        // canonical check: leftover bits must be zero (e.g. "QUJDRA==" fine, "QUJDRB==" not canonical)
        require(acc and ((1 shl bits) - 1) == 0) { "non-canonical trailing base64 bits" }
        require((pad == 0 && bits == 0) || (pad == 1 && bits == 2) || (pad == 2 && bits == 4)) { "padding disagrees with length" }
        return out.toByteArray()
    }
}
