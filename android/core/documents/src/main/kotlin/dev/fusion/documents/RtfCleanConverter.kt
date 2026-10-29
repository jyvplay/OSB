// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Clean-room Rich Text Format (`RTF 1.9.1`, `{\rtf1\ansi\deff0 ...}`) Writer & Security-Hardened Reader.
 *
 * Encodes & decodes paragraphs, bold (`\b`), italic (`\i`), underline (`\ul`), hex escapes (`\'hh`),
 * and signed-16-bit Unicode escapes (`\uN?`, where BMP code points `32768..65535` are encoded as
 * negative signed 16-bit integers `cp - 65536` per the RTF specification!), while strictly refusing
 * OLE `\object`, `\objdata`, and remote `INCLUDEPICTURE` exploit payloads (`CVE-2017-11882` / `CVE-2023-21716` class)!
 */
object RtfCleanConverter {
    const val SCOPE =
        "Clean-room RTF 1.9.1 writer & security-hardened reader (paragraphs, bold/italic/underline spans, signed-16-bit \\uN? Unicode escapes, and OLE \\object / \\objdata / INCLUDEPICTURE exploit refusal)."

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
    )

    data class Paragraph(val spans: List<Span>) {
        fun plainText(): String = spans.joinToString("") { it.text }
    }

    fun writeRtf(paragraphs: List<Paragraph>): String = buildString {
        append("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Arial;}}\\uc1\\pard ")
        paragraphs.forEachIndexed { idx, p ->
            if (idx > 0) append("\\par ")
            for (s in p.spans) {
                if (s.bold) append("\\b ")
                if (s.italic) append("\\i ")
                if (s.underline) append("\\ul ")
                append(escapeRtfText(s.text))
                if (s.underline) append("\\ul0 ")
                if (s.italic) append("\\i0 ")
                if (s.bold) append("\\b0 ")
            }
        }
        append("}")
    }

    fun readRtf(rtf: String): List<Paragraph> {
        val trimmed = rtf.trim()
        require(trimmed.startsWith("{\\rtf1")) { "invalid RTF header (expected {\\rtf1)" }
        for (exploit in listOf("\\object", "\\objdata", "\\objemb", "\\objocx", "INCLUDEPICTURE", "IMPORT")) {
            require(!trimmed.contains(exploit, ignoreCase = true)) {
                "dangerous RTF OLE/external token '$exploit' refused"
            }
        }
        val paragraphs = ArrayList<Paragraph>()
        var curSpans = ArrayList<Span>()
        val curText = StringBuilder()
        var bold = false
        var italic = false
        var underline = false

        fun flushSpan() {
            if (curText.isNotEmpty()) {
                curSpans.add(Span(curText.toString(), bold, italic, underline))
                curText.setLength(0)
            }
        }

        data class GroupState(val b: Boolean, val i: Boolean, val u: Boolean, val skipDest: Boolean)
        val stack = ArrayDeque<GroupState>()
        var skipDest = false
        var pos = 0

        while (pos < trimmed.length) {
            when (val ch = trimmed[pos]) {
                '{' -> {
                    stack.addLast(GroupState(bold, italic, underline, skipDest))
                    pos++
                    if (pos + 1 < trimmed.length && trimmed[pos] == '\\' && trimmed[pos + 1] == '*') {
                        skipDest = true
                        pos += 2
                    }
                }
                '}' -> {
                    flushSpan()
                    val prev = stack.removeLastOrNull() ?: break
                    bold = prev.b; italic = prev.i; underline = prev.u; skipDest = prev.skipDest
                    pos++
                }
                '\\' -> {
                    pos++
                    if (pos >= trimmed.length) break
                    val next = trimmed[pos]
                    if (next == '\\' || next == '{' || next == '}') {
                        if (!skipDest) curText.append(next)
                        pos++
                    } else if (next == '\'') {
                        require(pos + 2 < trimmed.length) { "truncated RTF hex escape" }
                        val byteVal = trimmed.substring(pos + 1, pos + 3).toInt(16)
                        if (!skipDest) curText.append(byteVal.toChar())
                        pos += 3
                    } else if (next.isLetter()) {
                        val wStart = pos
                        while (pos < trimmed.length && trimmed[pos].isLetter()) pos++
                        val word = trimmed.substring(wStart, pos)
                        var sign = 1
                        if (pos < trimmed.length && (trimmed[pos] == '-' || trimmed[pos] == '+')) {
                            if (trimmed[pos] == '-') sign = -1
                            pos++
                        }
                        val nStart = pos
                        while (pos < trimmed.length && trimmed[pos].isDigit()) pos++
                        val param = if (pos > nStart) trimmed.substring(nStart, pos).toInt() * sign else null
                        if (pos < trimmed.length && trimmed[pos] == ' ') pos++

                        if (word in setOf("fonttbl", "colortbl", "stylesheet", "info", "pict")) {
                            skipDest = true
                        } else if (!skipDest) {
                            when (word) {
                                "par", "line" -> {
                                    flushSpan()
                                    paragraphs.add(Paragraph(curSpans))
                                    curSpans = ArrayList()
                                }
                                "b" -> { flushSpan(); bold = (param != 0) }
                                "i" -> { flushSpan(); italic = (param != 0) }
                                "ul" -> { flushSpan(); underline = (param != 0) }
                                "ulnone" -> { flushSpan(); underline = false }
                                "pard", "plain" -> { flushSpan(); bold = false; italic = false; underline = false }
                                "tab" -> curText.append('\t')
                                "u" -> if (param != null) {
                                    val cp = if (param < 0) param + 65_536 else param
                                    curText.append(cp.toChar())
                                    if (pos < trimmed.length && trimmed[pos] == '?') pos++ // skip ANSI fallback char
                                }
                            }
                        }
                    } else {
                        pos++
                    }
                }
                '\r', '\n' -> pos++
                else -> {
                    if (!skipDest) curText.append(ch)
                    pos++
                }
            }
        }
        flushSpan()
        if (curSpans.isNotEmpty() || paragraphs.isEmpty()) {
            paragraphs.add(Paragraph(curSpans))
        }
        return paragraphs
    }

    private fun escapeRtfText(s: String): String = buildString(s.length) {
        for (ch in s) {
            val cp = ch.code
            when {
                ch == '\\' || ch == '{' || ch == '}' -> { append('\\'); append(ch) }
                cp in 32..126 -> append(ch)
                cp == 9 -> append("\\tab ")
                else -> {
                    val signed16 = if (cp > 32_767) cp - 65_536 else cp
                    append("\\u").append(signed16).append('?')
                }
            }
        }
    }
}
